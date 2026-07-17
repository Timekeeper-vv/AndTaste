#!/usr/bin/env python3
"""Generate idempotent SQL import for the 2026 product catalog / inventory Excel file.

The workbook is the supply-chain maintained product table. We import every row as
warehouse product master data, and create inventory ledger rows only when the
source sheet has an explicit positive current quantity.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

NS = {
    "a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
COLS = [chr(ord("A") + i) for i in range(26)]
FIELD_ORDER = [
    "source_file", "source_sheet", "excel_row_no", "product_name", "product_code", "product_ref_code",
    "box_code", "obsolete_primary_category", "primary_category", "obsolete_secondary_category", "secondary_category",
    "mold_type", "style_count", "initial_qty", "location_name", "sample_fee", "bulk_mold_fee",
    "mold_image_summary", "settlement_unit_price", "product_cost_unit_price", "company_cost_price",
    "image_summary", "spec_description", "cold_category", "duplicate_check_key", "source_created_at",
    "missing_check", "test_flag", "parent_record", "row_checksum", "raw_json", "enabled",
]
COL_TO_FIELD = {
    "A": "product_name",
    "B": "product_code",
    "C": "product_ref_code",
    "D": "box_code",
    "E": "obsolete_primary_category",
    "F": "primary_category",
    "G": "obsolete_secondary_category",
    "H": "secondary_category",
    "I": "mold_type",
    "J": "style_count",
    "K": "initial_qty",
    "L": "location_name",
    "M": "sample_fee",
    "N": "bulk_mold_fee",
    "O": "mold_image_summary",
    "P": "settlement_unit_price",
    "Q": "product_cost_unit_price",
    "R": "company_cost_price",
    "S": "image_summary",
    "T": "spec_description",
    "U": "cold_category",
    "V": "duplicate_check_key",
    "W": "source_created_at",
    "X": "missing_check",
    "Y": "test_flag",
    "Z": "parent_record",
}
NUMERIC_FIELDS = {"style_count", "initial_qty", "sample_fee", "bulk_mold_fee", "settlement_unit_price", "product_cost_unit_price", "company_cost_price"}


def clean_text(value: str | None) -> str:
    if value is None:
        return ""
    return str(value).replace("\u00a0", " ").strip()


def normalize_decimal(value: str | None) -> str | None:
    s = clean_text(value).replace(",", "")
    if not s:
        return None
    if re.fullmatch(r"[-+]?\d+(\.\d+)?", s):
        return s
    return None


def excel_serial_to_datetime(value: str | None) -> str | None:
    s = clean_text(value)
    if not s:
        return None
    try:
        serial = float(s)
    except ValueError:
        return s if re.match(r"\d{4}-\d{2}-\d{2}", s) else None
    base = dt.datetime(1899, 12, 30)
    return (base + dt.timedelta(days=serial)).replace(microsecond=0).strftime("%Y-%m-%d %H:%M:%S")


def sql_quote(value) -> str:
    if value is None or value == "":
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    s = str(value)
    s = s.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n")
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def resolve_workbook_target(target: str) -> str:
    return target.lstrip("/") if target.startswith("/") else "xl/" + target


def parse_xlsx(path: Path):
    with ZipFile(path) as zf:
        wb = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rid_to_target = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}
        shared: list[str] = []
        if "xl/sharedStrings.xml" in zf.namelist():
            root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
            for si in root.findall("a:si", NS):
                shared.append("".join((t.text or "") for t in si.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")))

        def cell_value(cell) -> str:
            cell_type = cell.attrib.get("t")
            v = cell.find("a:v", NS)
            if cell_type == "s":
                return shared[int(v.text)] if v is not None and v.text else ""
            if cell_type == "inlineStr":
                return "".join((t.text or "") for t in cell.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t"))
            return v.text if v is not None and v.text is not None else ""

        sheets_node = wb.find("a:sheets", NS)
        if sheets_node is None or len(sheets_node) == 0:
            raise RuntimeError("No worksheet found")
        sheet = sheets_node[0]
        sheet_name = sheet.attrib["name"]
        rid = sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]
        sheet_path = resolve_workbook_target(rid_to_target[rid])
        root = ET.fromstring(zf.read(sheet_path))
        rows: list[dict[str, str]] = []
        for row in root.findall(".//a:sheetData/a:row", NS):
            row_values: dict[str, str] = {}
            for cell in row.findall("a:c", NS):
                m = re.match(r"([A-Z]+)", cell.attrib["r"])
                if m:
                    row_values[m.group(1)] = cell_value(cell)
            if any(row_values.get(c, "") != "" for c in COLS):
                rows.append(row_values)
        return sheet_name, rows


def build_records(xlsx_path: Path):
    sheet_name, rows = parse_xlsx(xlsx_path)
    if not rows:
        raise RuntimeError("Empty worksheet")
    headers = {col: rows[0].get(col, "") for col in COLS}
    records = []
    seen_codes: set[str] = set()
    for offset, row in enumerate(rows[1:], start=2):
        code = clean_text(row.get("B"))
        name = clean_text(row.get("A"))
        if not code or not name or code in seen_codes:
            continue
        seen_codes.add(code)
        display_cells = {}
        raw_cells = {col: row.get(col, "") for col in COLS if row.get(col, "") != ""}
        for col in COLS:
            header = headers[col] or col
            field = COL_TO_FIELD.get(col)
            raw = row.get(col, "")
            if field == "source_created_at":
                display = excel_serial_to_datetime(raw) or ""
            elif field in NUMERIC_FIELDS:
                display = normalize_decimal(raw) or ""
            else:
                display = clean_text(raw)
            display_cells[header] = display
        checksum_source = json.dumps({"row": offset, "code": code, "rawCells": raw_cells}, ensure_ascii=False, sort_keys=True)
        checksum = hashlib.sha256(checksum_source.encode("utf-8")).hexdigest()
        rec = {
            "source_file": xlsx_path.name,
            "source_sheet": sheet_name,
            "excel_row_no": offset,
            "row_checksum": checksum,
            "raw_json": json.dumps({
                "sourceFile": xlsx_path.name,
                "sheet": sheet_name,
                "excelRow": offset,
                "headers": headers,
                "cells": display_cells,
                "rawCells": raw_cells,
            }, ensure_ascii=False, separators=(",", ":")),
            "enabled": 1,
        }
        for col, field in COL_TO_FIELD.items():
            raw = row.get(col, "")
            if field == "source_created_at":
                rec[field] = excel_serial_to_datetime(raw)
            elif field in NUMERIC_FIELDS:
                rec[field] = normalize_decimal(raw)
            else:
                rec[field] = clean_text(raw)
        records.append(rec)
    return sheet_name, headers, records


def safe_loc_name(name: str | None) -> str:
    return clean_text(name) or "产品主数据默认库位"


def inventory_records(records: list[dict]) -> list[dict]:
    out = []
    for r in records:
        qty = normalize_decimal(r.get("initial_qty"))
        if qty is None:
            continue
        try:
            if float(qty) <= 0:
                continue
        except ValueError:
            continue
        out.append(r)
    return out


def write_sql(records: list[dict], output: Path):
    output.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = [
        "-- Auto-generated by scripts/generate_product_inventory_sql.py",
        "-- Imports product master data from the supply-chain product table and seeds inventory only for rows with explicit current quantity.",
        f"-- Product catalog rows: {len(records)}",
        f"-- Initial inventory rows: {len(inventory_records(records))}",
        "",
    ]
    columns = ", ".join(f"`{c}`" for c in FIELD_ORDER)
    update_cols = [c for c in FIELD_ORDER if c not in {"product_code"}]
    update_sql = ", ".join(f"`{c}`=VALUES(`{c}`)" for c in update_cols)
    for rec in records:
        values = ", ".join(sql_quote(rec.get(c)) for c in FIELD_ORDER)
        lines.append(f"INSERT INTO warehouse_product_catalog ({columns}) VALUES ({values}) ON DUPLICATE KEY UPDATE {update_sql};")

    invs = inventory_records(records)
    if invs:
        lines.append("")
        lines.append("-- Seed source locations from non-empty current-stock rows.")
        loc_names = sorted({safe_loc_name(r.get("location_name")) for r in invs})
        for loc in loc_names:
            lines.append(
                "INSERT INTO warehouse_location (location_code,name,zone,capacity) VALUES "
                f"({sql_quote(loc)},{sql_quote(loc)},'产品表',999999.00) "
                "ON DUPLICATE KEY UPDATE name=VALUES(name), enabled=1;"
            )
        lines.append("")
        lines.append("-- Create inventory rows only once; subsequent imports update descriptive fields but do not overwrite live stock quantities.")
        for r in invs:
            loc = safe_loc_name(r.get("location_name"))
            code = r["product_code"]
            name = r["product_name"]
            spec = r.get("spec_description") or r.get("cold_category") or r.get("secondary_category") or ""
            qty = normalize_decimal(r.get("initial_qty")) or "0"
            lines.append(
                "INSERT INTO warehouse_inventory (item_type,item_id,item_code,item_name,spec,unit,location_id,stock_qty,locked_qty,available_qty,safety_stock,max_stock,last_in_at) "
                "SELECT 'SKU', p.id, "
                f"{sql_quote(code)}, {sql_quote(name)}, {sql_quote(spec)}, '件', l.id, {qty}, 0.00, {qty}, 0.00, 999999.00, NOW() "
                "FROM warehouse_product_catalog p JOIN warehouse_location l ON l.location_code=" + sql_quote(loc) + " "
                "WHERE p.product_code=" + sql_quote(code) + " "
                "ON DUPLICATE KEY UPDATE item_id=VALUES(item_id), item_name=VALUES(item_name), spec=VALUES(spec);"
            )
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xlsx", nargs="?", default="/Users/mac/Documents/之间味道-产品出入库管理 （2026正式启用）_产品表（供应链和项目维护）_表格（勿动）.xlsx")
    parser.add_argument("--output", default="shixun/src/main/resources/product_inventory_import.sql")
    args = parser.parse_args()
    xlsx = Path(args.xlsx).expanduser().resolve()
    sheet, headers, records = build_records(xlsx)
    write_sql(records, Path(args.output))
    print(json.dumps({
        "xlsx": str(xlsx),
        "sheet": sheet,
        "headers": headers,
        "productRows": len(records),
        "inventoryRows": len(inventory_records(records)),
        "output": args.output,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

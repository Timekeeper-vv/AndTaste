#!/usr/bin/env python3
"""Generate idempotent SQL import for 2026 bulk production approval Excel file.

Uses only the Python standard library so it works on a clean developer machine.
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
COLS = [
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI",
]
DATE_TIME_COLS = {"D", "E"}
DATE_COLS = {"AE", "AF", "AG"}
MONEY_COLS = {"W"}
INT_COLS = {"U"}
FIELD_ORDER = [
    "import_batch", "source_file", "source_sheet", "excel_row_no", "application_no", "approval_status",
    "approval_flow", "initiated_at", "completed_at", "initiator", "initiator_department", "current_handler",
    "approval_node", "application_department", "applicant", "project_name", "project_type", "project_level",
    "project_detail", "product_name", "product_code", "primary_category", "secondary_category", "production_type",
    "production_quantity", "spec_flavor", "unit_price", "unit_price_currency", "product_remark",
    "design_attachment_summary", "linked_approval", "contract_attachment_summary", "source_id", "work_order_status",
    "start_date", "estimated_complete_date", "actual_complete_date", "owner", "factory", "row_checksum", "raw_json",
]
COL_TO_FIELD = dict(zip(COLS, FIELD_ORDER[4:39]))


def excel_serial_to_datetime(value: str, as_date: bool = False) -> str | None:
    if value is None or str(value).strip() == "":
        return None
    try:
        serial = float(value)
    except ValueError:
        s = str(value).strip()
        return s[:10] if as_date and len(s) >= 10 else (s or None)
    base = dt.datetime(1899, 12, 30)
    result = base + dt.timedelta(days=serial)
    if as_date:
        return result.date().isoformat()
    result = result.replace(microsecond=0)
    return result.strftime("%Y-%m-%d %H:%M:%S")


def normalize_money(value: str) -> str | None:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    if not s:
        return None
    if re.fullmatch(r"[-+]?\d+(\.\d+)?", s):
        return s
    return None


def normalize_int(value: str) -> str | None:
    if value is None:
        return None
    s = str(value).strip().replace(",", "")
    if not s:
        return None
    if re.fullmatch(r"[-+]?\d+(\.0+)?", s):
        return str(int(float(s)))
    return None


def clean_text(value: str) -> str:
    if value is None:
        return ""
    # Keep identifiers readable while removing common copy/paste NBSP padding.
    return str(value).replace("\u00a0", " ").strip()


def sql_quote(value) -> str:
    if value is None or value == "":
        return "NULL"
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
        parsed_rows: list[dict[str, str]] = []
        for row in root.findall(".//a:sheetData/a:row", NS):
            row_values: dict[str, str] = {}
            for cell in row.findall("a:c", NS):
                m = re.match(r"([A-Z]+)", cell.attrib["r"])
                if not m:
                    continue
                row_values[m.group(1)] = cell_value(cell)
            if any(row_values.get(c, "") != "" for c in COLS):
                parsed_rows.append(row_values)
        return sheet_name, parsed_rows


def build_records(xlsx_path: Path):
    sheet_name, rows = parse_xlsx(xlsx_path)
    if not rows:
        raise RuntimeError("Empty worksheet")
    headers = {col: rows[0].get(col, "") for col in COLS}
    records = []
    for offset, row in enumerate(rows[1:], start=2):
        raw_cells = {col: row.get(col, "") for col in COLS if row.get(col, "") != ""}
        display_cells = {}
        for col in COLS:
            header = headers[col] or col
            raw = row.get(col, "")
            if col in DATE_TIME_COLS:
                display = excel_serial_to_datetime(raw, as_date=False)
            elif col in DATE_COLS:
                display = excel_serial_to_datetime(raw, as_date=True)
            else:
                display = clean_text(raw)
            display_cells[header] = display if display not in (None, "") else ""
        checksum_source = json.dumps({"row": offset, "rawCells": raw_cells}, ensure_ascii=False, sort_keys=True)
        checksum = hashlib.sha256(checksum_source.encode("utf-8")).hexdigest()
        rec = {
            "import_batch": sheet_name,
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
        }
        for col in COLS:
            field = COL_TO_FIELD[col]
            raw = row.get(col, "")
            if col in DATE_TIME_COLS:
                rec[field] = excel_serial_to_datetime(raw, as_date=False)
            elif col in DATE_COLS:
                rec[field] = excel_serial_to_datetime(raw, as_date=True)
            elif col in MONEY_COLS:
                rec[field] = normalize_money(raw)
            elif col in INT_COLS:
                rec[field] = normalize_int(raw)
            else:
                rec[field] = clean_text(raw)
        if not rec.get("source_id"):
            rec["source_id"] = f"BULK-ROW-{offset}-{checksum[:16]}"
        records.append(rec)
    return sheet_name, headers, records


def write_sql(records: list[dict], output: Path):
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "-- Auto-generated by scripts/generate_bulk_production_order_sql.py",
        "-- Idempotent import: one Excel product detail row becomes one supply_chain_bulk_production_order row.",
        "",
    ]
    if records:
        lines.append(f"-- Source rows (excluding header): {len(records)}")
    columns = ", ".join(f"`{c}`" for c in FIELD_ORDER)
    for rec in records:
        values = ", ".join(sql_quote(rec.get(c)) for c in FIELD_ORDER)
        lines.append(f"INSERT IGNORE INTO supply_chain_bulk_production_order ({columns}) VALUES ({values});")
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xlsx", nargs="?", default="/Users/mac/Documents/供应链工单明细_2026年大货生产审批_大货生产审批2026年 (2).xlsx")
    parser.add_argument("--output", default="shixun/src/main/resources/bulk_production_order_import.sql")
    args = parser.parse_args()
    xlsx = Path(args.xlsx).expanduser().resolve()
    sheet, headers, records = build_records(xlsx)
    write_sql(records, Path(args.output))
    source_ids = [r.get("source_id") for r in records]
    print(json.dumps({
        "xlsx": str(xlsx),
        "sheet": sheet,
        "output": args.output,
        "headers": headers,
        "rows": len(records),
        "uniqueSourceId": len(set(source_ids)),
        "blankSourceId": sum(1 for x in source_ids if not x),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

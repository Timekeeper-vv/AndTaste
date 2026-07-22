#!/usr/bin/env python3
"""
Blender headless converter for And Taste 3D assets.

Usage:
  blender -b --python scripts/model-convert-blender.py -- input.glb output.obj OBJ
  blender -b --python scripts/model-convert-blender.py -- input.glb output.stl STL
"""
import os
import sys

import bpy


def fail(message: str, code: int = 2):
    print(message, file=sys.stderr)
    raise SystemExit(code)


def main():
    if "--" not in sys.argv:
        fail("missing arguments after --")
    args = sys.argv[sys.argv.index("--") + 1 :]
    if len(args) < 3:
        fail("usage: input.glb output.obj|output.stl OBJ|STL")

    input_path = os.path.abspath(args[0])
    output_path = os.path.abspath(args[1])
    fmt = args[2].upper()
    if fmt not in {"OBJ", "STL"}:
        fail(f"unsupported output format: {fmt}")
    if not os.path.exists(input_path):
        fail(f"input file not found: {input_path}")

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()
    bpy.ops.import_scene.gltf(filepath=input_path)

    for obj in bpy.context.scene.objects:
        obj.select_set(obj.type == "MESH")
    mesh_count = sum(1 for obj in bpy.context.scene.objects if obj.type == "MESH")
    if mesh_count == 0:
        fail("no mesh objects found in GLB")

    if fmt == "OBJ":
        # Blender 4.x uses wm.obj_export; older versions use export_scene.obj.
        try:
            bpy.ops.wm.obj_export(filepath=output_path, export_selected_objects=False, path_mode="COPY")
        except Exception:
            bpy.ops.export_scene.obj(filepath=output_path, use_selection=False, path_mode="COPY")
    else:
        # Blender 4.x uses wm.stl_export; older versions use export_mesh.stl.
        try:
            bpy.ops.wm.stl_export(filepath=output_path, export_selected_objects=False)
        except Exception:
            bpy.ops.export_mesh.stl(filepath=output_path, use_selection=False)

    if not os.path.exists(output_path) or os.path.getsize(output_path) == 0:
        fail(f"output file was not created: {output_path}")
    print(f"converted {input_path} -> {output_path} ({fmt})")


if __name__ == "__main__":
    main()

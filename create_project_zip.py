import os
import zipfile
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ZIP_NAME = "Forex_Intelligence_Suite_v7_Enterprise_Upgraded.zip"
ZIP_PATH = os.path.join(BASE_DIR, ZIP_NAME)

EXCLUDE_DIRS = {
    ".git", ".idea", ".vscode", "__pycache__", ".pytest_cache",
    "target", ".mvn", "node_modules", "h2db"
}

EXCLUDE_EXTS = {
    ".pyc", ".pyo", ".log", ".mv.db", ".trace.db", ".lock"
}

def create_zip():
    print(f"📦 Packaging clean project archive to: {ZIP_NAME}...")
    file_count = 0
    with zipfile.ZipFile(ZIP_PATH, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(BASE_DIR):
            # Prune excluded directories
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]

            for file in files:
                if file == ZIP_NAME:
                    continue
                _, ext = os.path.splitext(file)
                if ext.lower() in EXCLUDE_EXTS:
                    continue

                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, BASE_DIR)
                try:
                    zipf.write(full_path, rel_path)
                    file_count += 1
                except (PermissionError, OSError) as e:
                    print(f"  [Notice] Skipping locked file: {rel_path}")

    size_mb = os.path.getsize(ZIP_PATH) / (1024 * 1024)
    print(f"✅ Archive created successfully!")
    print(f"   Files compressed: {file_count}")
    print(f"   File size:        {size_mb:.2f} MB")
    print(f"   Output path:      {ZIP_PATH}")

if __name__ == "__main__":
    create_zip()

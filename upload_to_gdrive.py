#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==============================================================================
FOREX INTELLIGENCE SUITE v7.0 — AUTOMATED GOOGLE DRIVE CLOUD UPLOADER
==============================================================================
Uploads `Forex_Intelligence_Suite_v7_Enterprise_Upgraded.zip` to Google Drive
using Google Drive API v3 (OAuth2 / Service Account) or rclone/gdrive CLI,
sets public reader permissions (`anyoneWithLink`), and prints the shareable URL.

Usage:
  1. Using Service Account or OAuth2 credentials.json:
     python3 upload_to_gdrive.py --file Forex_Intelligence_Suite_v7_Enterprise_Upgraded.zip --credentials credentials.json

  2. Using Google Drive CLI (gdrive / rclone):
     gdrive files upload Forex_Intelligence_Suite_v7_Enterprise_Upgraded.zip
     gdrive permissions share <FILE_ID>
==============================================================================
"""

import argparse
import os
import sys
from pathlib import Path


def upload_via_google_api(file_path: Path, credentials_path: Path, folder_id: str = "") -> str:
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload
    except ImportError:
        raise RuntimeError(
            "Google API client libraries not installed. Run: "
            "pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib"
        )

    scopes = ["https://www.googleapis.com/auth/drive"]
    creds = service_account.Credentials.from_service_account_file(str(credentials_path), scopes=scopes)
    service = build("drive", "v3", credentials=creds)

    metadata = {"name": file_path.name}
    if folder_id:
        metadata["parents"] = [folder_id]

    media = MediaFileUpload(str(file_path), mimetype="application/zip", resumable=True)
    print(f"[*] Uploading {file_path.name} ({file_path.stat().st_size:,} bytes) to Google Drive...")
    created = service.files().create(
        body=metadata,
        media_body=media,
        fields="id, name, webViewLink, webContentLink"
    ).execute()

    file_id = created["id"]
    service.permissions().create(
        fileId=file_id,
        body={"type": "anyone", "role": "reader"},
        fields="id"
    ).execute()

    share_url = created.get("webViewLink") or f"https://drive.google.com/file/d/{file_id}/view?usp=sharing"
    print(f"[OK] Upload Complete! File ID: {file_id}")
    print(f"[OK] Public Shareable Download Link: {share_url}")
    return share_url


def main() -> None:
    parser = argparse.ArgumentParser(description="Upload Forex Intelligence Suite v7 Archive to Google Drive")
    parser.add_argument(
        "--file",
        default="Forex_Intelligence_Suite_v7_Enterprise_Upgraded.zip",
        help="Path to the ZIP archive to upload"
    )
    parser.add_argument(
        "--credentials",
        default=os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "credentials.json"),
        help="Path to Google Cloud Service Account / OAuth JSON credentials"
    )
    parser.add_argument(
        "--folder-id",
        default=os.getenv("GDRIVE_FOLDER_ID", ""),
        help="Optional Google Drive target folder ID"
    )
    args = parser.parse_args()

    archive_path = Path(args.file).resolve()
    if not archive_path.exists():
        print(f"[ERROR] Archive not found: {archive_path}", file=sys.stderr)
        sys.exit(1)

    creds_path = Path(args.credentials)
    if creds_path.exists():
        upload_via_google_api(archive_path, creds_path, args.folder_id)
    else:
        print("==============================================================================")
        print("  GOOGLE DRIVE CLOUD UPLOAD — CLI & API INSTRUCTIONS")
        print("==============================================================================")
        print(f"Target Archive: {archive_path} ({archive_path.stat().st_size:,} bytes)")
        print("\nOption A — Google Drive API (Service Account):")
        print("  export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service_account.json")
        print(f"  python3 upload_to_gdrive.py --file {archive_path.name}")
        print("\nOption B — Official gdrive CLI:")
        print(f"  gdrive files upload {archive_path.name}")
        print("  gdrive permissions share <FILE_ID>")
        print("\nOption C — rclone Cloud Sync:")
        print(f"  rclone copy {archive_path.name} gdrive:Forex_Intelligence_Suite_v7/")
        print(f"  rclone link gdrive:Forex_Intelligence_Suite_v7/{archive_path.name}")
        print("==============================================================================")


if __name__ == "__main__":
    main()

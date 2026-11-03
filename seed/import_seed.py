"""Importe seed/03_DONNEES_INITIALES.json dans VOTRE compte Supabase (une seule fois).

Usage :
  SUPABASE_URL=https://xxx.supabase.co SUPABASE_ANON_KEY=sb_publishable_xxx python3 seed/import_seed.py vous@example.fr
Le mot de passe est demandé interactivement (jamais stocké). Utilise le JWT de l'utilisateur : le RLS s'applique.
"""
import getpass
import json
import os
import pathlib
import sys

import requests

url, key = os.environ["SUPABASE_URL"].rstrip("/"), os.environ["SUPABASE_ANON_KEY"]
email = sys.argv[1]
r = requests.post(f"{url}/auth/v1/token?grant_type=password", headers={"apikey": key},
                  json={"email": email, "password": getpass.getpass("Mot de passe Supabase : ")}, timeout=20)
r.raise_for_status()
jwt = r.json()["access_token"]
seed = json.loads((pathlib.Path(__file__).parent / "03_DONNEES_INITIALES.json").read_text())
r = requests.post(f"{url}/rest/v1/rpc/import_initial_dataset", json={"p": seed}, timeout=60,
                  headers={"apikey": key, "Authorization": f"Bearer {jwt}"})
print(r.status_code, r.text)
sys.exit(0 if r.ok else 1)

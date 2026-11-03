"""Vérification statique du contrat JSON ↔ modèles Kotlin (exécutable sans SDK Android).

Pour chaque fixture (réponses RÉELLES des fonctions SQL), décode récursivement comme le ferait
kotlinx.serialization (SnakeCase, ignoreUnknownKeys) et signale :
  * un champ Kotlin obligatoire (sans valeur par défaut) absent du JSON  → échec de décodage à l'exécution ;
  * un type incompatible (objet vs liste vs scalaire) ;
  * les champs Kotlin jamais vus dans aucune fixture (faute de frappe probable dans le nom).
Usage : python3 android/tools/check_models.py
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MODELS = (ROOT / "app/src/main/java/fr/coachsportif/app/domain/Models.kt").read_text()
FIX = ROOT / "app/src/test/resources/fixtures"

classes = {}
for m in re.finditer(r"data class (\w+)\(", MODELS):
    depth, i = 1, m.end()
    while depth:
        depth += {"(": 1, ")": -1}.get(MODELS[i], 0); i += 1
    body = MODELS[m.end():i - 1]
    fields = []
    for line in re.split(r",\s*(?=(?:@SerialName\([^)]*\)\s*)?val )|\n", body):
        f = re.match(r'\s*(?:@SerialName\("([^"]+)"\)\s*)?val (\w+): ([\w<>?, ]+?)(\s*=.*)?,?\s*$', line)
        if f:
            fields.append((f.group(1), f.group(2), f.group(3).strip(), f.group(4) is not None))
    classes[m.group(1)] = fields


def snake(name):  # JsonNamingStrategy.SnakeCase : chiffres traités comme des minuscules
    return re.sub(r"(?<!^)([A-Z])", r"_\1", name).lower()


seen = set()
errors = []


def check(cls, obj, path):
    if not isinstance(obj, dict):
        errors.append(f"{path}: objet attendu pour {cls}, reçu {type(obj).__name__}")
        return
    for serial, name, typ, has_default in classes[cls]:
        key = serial or snake(name)
        nullable = typ.endswith("?")
        base = typ.rstrip("?")
        if key not in obj or obj[key] is None:
            if not has_default and not nullable:
                errors.append(f"{path}.{key}: champ obligatoire {cls}.{name} absent")
            continue
        seen.add((cls, name))
        v = obj[key]
        lm = re.match(r"List<(\w+)>", base)
        if lm:
            if not isinstance(v, list):
                errors.append(f"{path}.{key}: liste attendue")
                continue
            inner = lm.group(1)
            for i, x in enumerate(v):
                if inner in classes:
                    check(inner, x, f"{path}.{key}[{i}]")
        elif base in classes:
            check(base, v, f"{path}.{key}")
        elif base == "JsonObject" and not isinstance(v, dict):
            errors.append(f"{path}.{key}: JsonObject attendu, reçu {v!r}")
        elif base in ("Int", "Long") and not (isinstance(v, int) and not isinstance(v, bool)):
            errors.append(f"{path}.{key}: entier attendu, reçu {v!r}")
        elif base == "Double" and not isinstance(v, (int, float)):
            errors.append(f"{path}.{key}: nombre attendu, reçu {v!r}")
        elif base == "String" and not isinstance(v, str):
            errors.append(f"{path}.{key}: chaîne attendue, reçu {v!r}")
        elif base == "Boolean" and not isinstance(v, bool):
            errors.append(f"{path}.{key}: booléen attendu, reçu {v!r}")


mapping = {"day_": "DayPlan", "week": "WeekPlan", "summary": "WeekSummary", "history_": "ExerciseHistory", "recent": "RecentSessions"}
for f in sorted(FIX.glob("*.json")):
    cls = next(c for p, c in mapping.items() if f.stem.startswith(p))
    check(cls, json.loads(f.read_text()), f.stem)

response_classes = {"DayPlan", "PlannedActivity", "LastChange", "ExerciseRef", "VariantReference", "LastLogged", "SetOut",
                    "CompletedActivity", "Endurance", "CompletedExercise", "WeekPlan", "WeekDay", "EventOut", "WeekSummary",
                    "ExerciseHistory", "HistorySession", "RecentSessions"}
never = [f"{c}.{n}" for c in sorted(response_classes) for (_, n, _, _) in classes[c] if (c, n) not in seen]
print(f"{len(classes)} classes analysées, {len(list(FIX.glob('*.json')))} fixtures.")
print("Champs jamais rencontrés (normal s'ils sont optionnels et absents du jeu de test) :", ", ".join(never) or "aucun")
if errors:
    print("ERREURS :"); [print("  " + e) for e in errors]; sys.exit(1)
print("OK : toutes les fixtures se décodent avec les modèles Kotlin.")

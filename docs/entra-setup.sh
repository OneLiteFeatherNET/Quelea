#!/bin/bash
# Creates the Quelea Entra ID registrations (idempotent): the API registration with the app role Quelea.Client,
# and the Velocity client-credentials registration that holds that role (admin consent is part of the app role
# assignment). Needs the Azure CLI (az login as a user who may create app registrations and grant consent) and
# uuidgen. Safe to run again: existing objects are reused, and the client secret is only created when the env
# file has none. See docs/entra-setup.md.
#
# Usage: docs/entra-setup.sh [env-file]
#   env-file  where the ids and the client secret are written (mode 600). Default: ~/.config/quelea/entra.env.
#             The file is a secret: keep it out of git and out of chat.
set -euo pipefail

OUT="${1:-$HOME/.config/quelea/entra.env}"
GRAPH=https://graph.microsoft.com/v1.0
ROLE=Quelea.Client

command -v uuidgen > /dev/null || { echo "uuidgen is required" >&2; exit 1; }
command -v az > /dev/null || { echo "the Azure CLI (az) is required" >&2; exit 1; }
TENANT=$(az account show --query tenantId -o tsv)
[ -n "$TENANT" ] || { echo "no tenant id: run az login first" >&2; exit 1; }
mkdir -p "$(dirname "$OUT")"

app() { # displayName -> appId
  local id
  id=$(az ad app list --filter "displayName eq '$1'" --query "[0].appId" -o tsv)
  [ -n "$id" ] || id=$(az ad app create --display-name "$1" --sign-in-audience AzureADMyOrg --query appId -o tsv)
  echo "$id"
}
sp() { az ad sp show --id "$1" --query id -o tsv 2>/dev/null || az ad sp create --id "$1" --query id -o tsv; }

# --- API: the audience of every token; one app role, no delegated scope (no user signs in to Quelea) ---
API=$(app "Quelea API")
API_OBJ=$(az ad app show --id "$API" --query id -o tsv)
ROLE_ID=$(az ad app show --id "$API" --query "appRoles[?value=='$ROLE'].id | [0]" -o tsv)
[ -n "$ROLE_ID" ] || ROLE_ID=$(uuidgen)
az rest --method PATCH --url "https://graph.microsoft.com/v1.0/applications/$API_OBJ" \
  --headers Content-Type=application/json --body @- > /dev/null <<EOF_JSON
{
  "identifierUris": ["api://$API"],
  "api": {"requestedAccessTokenVersion": 2},
  "appRoles": [{
    "id": "$ROLE_ID", "value": "$ROLE", "displayName": "Quelea client",
    "description": "Application that calls the Quelea party API.",
    "allowedMemberTypes": ["Application"], "isEnabled": true
  }]
}
EOF_JSON
API_SP=$(sp "$API")

# --- Velocity: client credentials, holds Quelea.Client ---
VEL=$(app "Quelea Velocity")
VEL_OBJ=$(az ad app show --id "$VEL" --query id -o tsv)
az rest --method PATCH --url "$GRAPH/applications/$VEL_OBJ" --headers Content-Type=application/json \
  --body "{\"requiredResourceAccess\":[{\"resourceAppId\":\"$API\",\"resourceAccess\":[{\"id\":\"$ROLE_ID\",\"type\":\"Role\"}]}]}" > /dev/null
VEL_SP=$(sp "$VEL")
has=$(az rest --method GET --url "$GRAPH/servicePrincipals/$VEL_SP/appRoleAssignments" \
  --query "value[?appRoleId=='$ROLE_ID'] | length(@)" -o tsv)
[ "$has" != "0" ] || az rest --method POST --url "$GRAPH/servicePrincipals/$VEL_SP/appRoleAssignments" \
  --headers Content-Type=application/json \
  --body "{\"principalId\":\"$VEL_SP\",\"resourceId\":\"$API_SP\",\"appRoleId\":\"$ROLE_ID\"}" > /dev/null

# The secret is kept: a rerun reuses the one in the env file. Rotate it by deleting the line first.
SECRET=""
if [ -f "$OUT" ]; then SECRET=$(sed -n 's/^QUELEA_CLIENT_SECRET=//p' "$OUT"); fi
if [ -z "$SECRET" ]; then
  SECRET=$(az ad app credential reset --id "$VEL" --display-name "quelea" --years 1 --append --query password -o tsv)
fi

umask 077
cat > "$OUT.new" <<EOF_ENV
# Quelea Entra ID set, tenant $TENANT. Secret file: do not commit or share.
# Backend (Kubernetes-FLUX): the QUELEA_AUTH_* lines.
QUELEA_AUTH_TENANT_ID=$TENANT
QUELEA_AUTH_AUDIENCE=$API
QUELEA_AUTH_ISSUER=https://login.microsoftonline.com/$TENANT/v2.0
QUELEA_AUTH_JWKS_URL=https://login.microsoftonline.com/$TENANT/discovery/v2.0/keys
# Velocity plugin (CloudNet proxy template): the QUELEA_TENANT_ID, QUELEA_CLIENT_* and QUELEA_AUTH_SCOPE lines.
QUELEA_TENANT_ID=$TENANT
QUELEA_CLIENT_ID=$VEL
QUELEA_CLIENT_SECRET=$SECRET
QUELEA_AUTH_SCOPE=api://$API/.default
EOF_ENV
chmod 600 "$OUT.new"
mv "$OUT.new" "$OUT"
echo "done; values written to $OUT (mode 600)."

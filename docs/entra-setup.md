# Entra ID setup

How to register the Quelea API and its clients in Microsoft Entra ID. Creating app registrations and granting
consent are admin actions; the Free tier is enough.

Quelea accepts only app-only access tokens (client credentials). A person never signs in to Quelea, so no token of
a signed-in user is accepted. Each client carries the app role `Quelea.Client`, and the service checks the token
itself: signature, issuer, audience, tenant and expiry.

## What gets created

| Object | Name | Purpose |
|--------|------|---------|
| API registration | `Quelea API` | The audience of every token. Its app role `Quelea.Client` (allowed member type: Applications). Its identifier URI is `api://<client-id>`. |
| Velocity registration | `Quelea Velocity` | The proxy plugin: client credentials and a client secret. It holds `Quelea.Client` (application permission with admin consent). |

Settings that matter on the API registration:

- `requestedAccessTokenVersion: 2`. Without it Entra issues v1 tokens with another issuer and audience, and the
  service refuses them.
- No delegated scope. Quelea has no user sign-in.

Later clients (the lobby, for example) get their own registration and the same app role.

## Run the script

`docs/entra-setup.sh` creates both registrations and is safe to run again. It needs the Azure CLI and `uuidgen`;
sign in with `az login` as an Entra admin first.

```shell
docs/entra-setup.sh                      # writes ~/.config/quelea/entra.env (mode 600)
docs/entra-setup.sh /path/to/entra.env   # or to another file
```

The script prints nothing secret. The env file holds the tenant id, the API client id and the Velocity client id
and secret. Keep it out of git and out of chat.

### Where each value goes

| Env file line | Used by |
|---------------|---------|
| `QUELEA_AUTH_TENANT_ID`, `QUELEA_AUTH_AUDIENCE`, `QUELEA_AUTH_ISSUER`, `QUELEA_AUTH_JWKS_URL` | Backend (Kubernetes-FLUX) |
| `QUELEA_TENANT_ID`, `QUELEA_CLIENT_ID`, `QUELEA_CLIENT_SECRET`, `QUELEA_AUTH_SCOPE` | Velocity plugin (CloudNet proxy template) |

`QUELEA_AUTH_AUDIENCE` is the API's application (client) id, a GUID. The backend refuses to start outside the `dev`
and `test` environments unless the audience is that GUID and the tenant and an https issuer are set.
`QUELEA_AUTH_SCOPE` is `api://<api-client-id>/.default`.

## Rotating the Velocity secret

Secrets expire (the script uses one year). Rotate without downtime:

1. Create a new secret next to the old one:
   `az ad app credential reset --id <velocity-client-id> --display-name quelea-<year> --years 1 --append --query password -o tsv`
2. Put it into `QUELEA_CLIENT_SECRET` (CloudNet proxy template) and restart the proxies.
3. Check that the proxies still reach the service, then delete the old secret:
   `az ad app credential list --id <velocity-client-id>` and
   `az ad app credential delete --id <velocity-client-id> --key-id <old-key-id>`.

Do not re-run the script to rotate: it keeps the secret already in the env file.

## Checking it works

Request a token for the API with the client credentials of the Velocity registration and call the service:
a request with that token reaches the routes, and a request without a token answers 401 `unauthorized`. A token
without the role answers 403 `forbidden`.

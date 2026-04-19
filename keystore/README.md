# Release Signing Keystore

This directory is intentionally **empty** and not tracked by version control.

## Generating a release keystore

Run the following command to create a new keystore:

```bash
keytool -genkey \
  -v \
  -keystore release.keystore \
  -alias rizzo-player \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <your-store-password> \
  -keypass <your-key-password> \
  -dname "CN=RizzoPlayer, OU=Android, O=RizzoPlayer, L=City, ST=State, C=US"
```

Replace `<your-store-password>` and `<your-key-password>` with strong passwords.
**Do not lose these passwords** — there is no recovery option.

## Configuring local.properties

After generating the keystore, add the following to `local.properties` in the project root
(never commit this file to version control):

```
RELEASE_KEYSTORE_PATH=/absolute/path/to/release.keystore
RELEASE_KEYSTORE_PASSWORD=<your-store-password>
RELEASE_KEY_ALIAS=rizzo-player
RELEASE_KEY_PASSWORD=<your-key-password>
```

## Security notes

- The `local.properties` file is already in `.gitignore`.
- Never commit `.keystore` files to any repository.
- Store your keystore on a encrypted drive or hardware key manager.
- If you lose your keystore, you will not be able to publish updates to existing app listings.

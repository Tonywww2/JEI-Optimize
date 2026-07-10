# External Performance Data Collection

Use this when testing JEI Optimize in a real modpack or launcher-managed Minecraft instance outside this development workspace.

The collector does not start or stop Minecraft. It edits the JEI Optimize config when requested, waits for the instance log to report JEI startup, then packages metrics and supporting files into a zip.

## What It Collects

- JEI startup metrics parsed from `logs/latest.log`.
- `latest.log`, a captured log segment, and `debug.log` when present.
- JEI-related config snapshots from `config/`.
- A `mods-manifest.csv` with mod jar names, sizes, timestamps, and SHA-256 hashes.
- Basic OS, CPU, PowerShell, and Java-from-PATH information.
- Recent crash reports when present.

The package may contain local paths, usernames, hardware details, and full logs. Inspect the zip before sharing it publicly.

## Profiles

- `baseline`: writes `general.enabled=false` and disables all JEI Optimize feature flags.
- `optimized`: writes `general.enabled=true` and enables all JEI Optimize sync/async feature flags.
- `observe`: does not edit config; only watches and packages the current run.

For `baseline` and `optimized`, the original `config/jei_optimize-client.toml` is restored after collection unless `-NoRestoreConfig` is specified.

## Recommended Two-Run Procedure

Close Minecraft before starting each collection run.

1. Baseline run:

```powershell
.\scripts\collect-external-jei-optimize.ps1 `
  -InstanceDir "D:\Instances\MyPack\.minecraft" `
  -Profile baseline `
  -TimeoutMinutes 10
```

When the script says it is watching `latest.log`, start the instance with your launcher, enter the same test world, and wait for JEI to finish loading.

2. Optimized run:

```powershell
.\scripts\collect-external-jei-optimize.ps1 `
  -InstanceDir "D:\Instances\MyPack\.minecraft" `
  -Profile optimized `
  -TimeoutMinutes 10
```

Use the same launcher profile, world, graphics settings, and interaction path as the baseline run.

3. Send back the generated zip files from:

```text
<instance>\jei-optimize-external-results\*.zip
```

## Observe Existing Runs

If you do not want the collector to change config, use:

```powershell
.\scripts\collect-external-jei-optimize.ps1 `
  -InstanceDir "D:\Instances\MyPack\.minecraft" `
  -Profile observe `
  -TimeoutMinutes 10
```

Start the script before launching Minecraft. Add `-ReadExistingLog` only when you intentionally want to parse an already-running or already-finished `latest.log`.

## Launcher Notes

- Prism/MultiMC: use the instance `.minecraft` folder.
- CurseForge: use the profile folder that contains `mods`, `config`, and `logs`.
- Modrinth App: use the instance folder that contains `mods`, `config`, and `logs`.

The collector expects these paths inside `-InstanceDir`:

```text
config\jei_optimize-client.toml
logs\latest.log
mods\
```
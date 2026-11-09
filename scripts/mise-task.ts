#!/usr/bin/env bun
import { publishRelease } from "../../Scripts/releases/aurora-publisher.ts";
import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { resolve, join } from "node:path";

const root = resolve(import.meta.dir, "..");

function readJson(path: string): any {
    return JSON.parse(readFileSync(path, "utf-8"));
}

function sha256(filePath: string): string {
    const buffer = readFileSync(filePath);
    return createHash("sha256").update(buffer).digest("hex");
}

function runCmdSync(cmd: string[], cwd = root): { stdout: string; stderr: string; code: number } {
    const res = Bun.spawnSync(cmd, { cwd, stdout: "pipe", stderr: "pipe" });
    return {
        stdout: res.stdout.toString().trim(),
        stderr: res.stderr.toString().trim(),
        code: res.exitCode ?? 0,
    };
}

function getVersionCode(): number {
    try {
        const props = readFileSync(join(root, "gradle.properties"), "utf-8");
        const match = props.match(/^lunafetch\.versionCode=(\d+)$/m);
        return match ? parseInt(match[1], 10) : 26;
    } catch {
        return 26;
    }
}

const pkg = readJson(join(root, "package.json"));
const appVersion = pkg.version;
const appName = "Luna Fetch";
const repository = "biglexj/Luna---Fetch";

const help = `Luna Fetch: mise <dev|build|check|test|release|publish> [opciones]
  dev      Ejecutar en modo desarrollo (:composeApp:run).
  build    Compilar empaquetado de escritorio (:composeApp:packageExe) sin instalador.
  check    Compilar el código Kotlin (:composeApp:compileKotlinDesktop) sin generar artefactos.
  test     Ejecutar las pruebas unitarias (:composeApp:desktopTest).
  release  Generar instalador Inno Setup, APK y SHA256SUMS.txt en release/.
  publish  [github|all] Publicar en GitHub y sincronizar ficha en Aurora.
  --plan   Mostrar los comandos o el plan sin ejecutar acciones ni escribir.
  --check  Para publish: verificar requisitos locales y remotos sin escribir.

Ejemplos:
  mise dev
  mise dev --plan
  mise build --plan
  mise check --plan
  mise test --plan
  mise release --plan
  mise publish --plan
  mise publish github --plan
  mise publish --check
`;

async function handlePublish(args: string[]): Promise<number> {
    const isPlan = args.includes("--plan");
    const isCheck = args.includes("--check");
    const positional = args.filter((arg) => !arg.startsWith("-"));
    const channel = positional[0] ?? "all";

    if (!["all", "github"].includes(channel)) {
        throw new Error(`Canal desconocido: ${channel}. Usa 'github' o 'all'.`);
    }

    const effectiveChannels = channel === "github" ? ["GitHub Releases"] : ["GitHub Releases", "Aurora Web (biglexj.com)"];

    console.log(`\n=== Publicación: ${appName} v${appVersion} ===`);
    console.log(`Repositorio : ${repository}`);
    console.log(`Canales     : ${effectiveChannels.join(" + ")}`);

    if (isPlan) {
        console.log("\n[Plan de publicación]");
        console.log("1. Verificar que el repositorio esté en rama limpia y commit en remoto.");
        console.log(`2. Comprobar instalador local en release/: LunaFetch-Windows-${appVersion}.exe (o variante).`);
        console.log("3. Validar RELEASE_MESSAGE.md (notas de versión públicas sin secretos).");
        console.log(`4. Crear tag git v${appVersion} y GitHub Release vía gh.`);
        if (channel === "all") {
            console.log("5. Sincronizar metadatos y enlace de descarga en Aurora (https://www.biglexj.com).");
        } else {
            console.log("5. Sincronización con Aurora omitida (canal explícito: github).");
        }
        console.log("\n(--plan completado: no se realizaron cambios ni consultas de red)");
        return 0;
    }

    if (isCheck) {
        console.log("\n[Comprobación de preparación (--check)]");
        let hasErrors = false;

        // 1. Git status
        const gitStatus = runCmdSync(["git", "status", "--porcelain"]);
        if (gitStatus.stdout) {
            console.warn("⚠️  Aviso: El repositorio tiene cambios locales sin confirmar.");
        } else {
            console.log("✅ Árbol Git limpio.");
        }

        // 2. Git tag
        const tagCheck = runCmdSync(["git", "tag", "-l", `v${appVersion}`]);
        if (tagCheck.stdout.includes(`v${appVersion}`)) {
            console.warn(`⚠️  El tag v${appVersion} ya existe localmente.`);
        } else {
            console.log(`✅ Tag v${appVersion} libre localmente.`);
        }

        // 3. Artefactos en release/
        const releaseDir = join(root, "release");
        let foundInstaller: string | null = null;
        if (existsSync(releaseDir)) {
            const files = readdirSync(releaseDir);
            foundInstaller = files.find((f) => f.startsWith("LunaFetch") && f.endsWith(".exe")) ?? null;
        }

        if (foundInstaller) {
            const fullPath = join(releaseDir, foundInstaller);
            const st = statSync(fullPath);
            const hash = sha256(fullPath);
            console.log(`✅ Instalador presente: ${foundInstaller} (${(st.size / 1024 / 1024).toFixed(2)} MB)`);
            console.log(`   SHA-256: ${hash}`);
        } else {
            console.warn(`❌ Instalador no encontrado en release/LunaFetch-*.exe. Ejecuta 'mise release' antes de publicar.`);
            hasErrors = true;
        }

        // 4. Notas de versión
        const notesPath = join(root, "RELEASE_MESSAGE.md");
        if (existsSync(notesPath)) {
            console.log("✅ RELEASE_MESSAGE.md presente.");
        } else {
            console.warn("⚠️  RELEASE_MESSAGE.md no encontrado en la raíz.");
        }

        console.log("\n(--check completado: modo solo lectura sin escrituras)");
        return hasErrors ? 1 : 0;
    }

    console.error("\n❌ Publicación bloqueada: Requiere confirmación y autorización explícita para publicar en producción.");
    console.error("   Usa --plan para revisar los pasos previstos o --check para verificar el estado de los artefactos.");
    return 1;
}

export async function main(args = Bun.argv.slice(2)): Promise<number> {
    const [action, ...rest] = args;
    if (!action || action === "--help" || rest.includes("--help")) {
        console.log(help);
        return 0;
    }


    const isPlan = rest.includes("--plan");

    if (action === "dev") {
        const isAndroid = rest.some((arg) => arg.toLowerCase() === "android");
        const cmd = isAndroid
            ? ["cmd", "/c", "gradlew.bat", ":composeApp:installDebug"]
            : ["cmd", "/c", "gradlew.bat", ":composeApp:run"];
        console.log(`dev ${appName} (${isAndroid ? "Android" : "Desktop"})\nDirectorio: ${root}\nComando: ${cmd.join(" ")}`);
        if (isPlan) return 0;
        const child = Bun.spawn(cmd, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
        return (await child.exited) ?? 0;
    }

    if (action === "check") {
        const cmd = ["cmd", "/c", "gradlew.bat", ":composeApp:compileKotlinDesktop"];
        console.log(`check ${appName}\nDirectorio: ${root}\nComando: ${cmd.join(" ")}`);
        if (isPlan) return 0;
        const child = Bun.spawn(cmd, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
        return (await child.exited) ?? 0;
    }

    if (action === "test") {
        const cmd = ["cmd", "/c", "gradlew.bat", ":composeApp:desktopTest"];
        console.log(`test ${appName}\nDirectorio: ${root}\nComando: ${cmd.join(" ")}`);
        if (isPlan) return 0;
        const child = Bun.spawn(cmd, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
        return (await child.exited) ?? 0;
    }

    if (action === "build") {
        const isAndroid = rest.some((arg) => arg.toLowerCase() === "android");
        const cmd = isAndroid
            ? ["cmd", "/c", "gradlew.bat", ":composeApp:assembleRelease"]
            : ["cmd", "/c", "gradlew.bat", ":composeApp:packageExe"];
        console.log(`build ${appName} (${isAndroid ? "Android" : "Desktop"})\nDirectorio: ${root}\nComando: ${cmd.join(" ")}`);
        if (isPlan) return 0;

        console.log(`\n==> Compilando ${isAndroid ? "APK de Android" : "ejecutable de escritorio"} Compose Multiplatform...`);
        const child = Bun.spawn(cmd, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
        return (await child.exited) ?? 0;
    }

    if (action === "release") {
        const psArgs = [
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", "./scripts/build/build-release.ps1", "-LocalOnly",
        ];
        console.log(`release ${appName}\nDirectorio: ${root}\nComando: ${psArgs.join(" ")}`);
        if (isPlan) return 0;

        const child = Bun.spawn(psArgs, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
        return (await child.exited) ?? 0;
    }

    if (action === "publish") {
        return publishRelease({
            slug: "luna-fetch",
            appName: "Luna Fetch",
            repository: "biglexj/Luna---Fetch",
            version: appVersion,
            versionCode: getVersionCode(),
            projectRoot: root,
            primaryAssetPattern: new RegExp(`^LunaFetch.*\\.exe$`, "i"),
            build: async (isPlan) => {
                const psArgs = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "./scripts/build/build-release.ps1", "-LocalOnly"];
                if (isPlan) return 0;
                const child = Bun.spawn(psArgs, { cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit" });
                return (await child.exited) ?? 0;
            }
        }, rest);
    }

    throw new Error(`Acción desconocida: ${action}. Usa mise dev, mise build, mise release o mise publish.`);
}

if (import.meta.main) {
    const args = process.env.MISE_TASK_NAME
        ? [process.env.MISE_TASK_NAME, ...Bun.argv.slice(2)]
        : Bun.argv.slice(2);
    try {
        process.exit(await main(args));
    } catch (err: any) {
        console.error(err?.message ?? err);
        process.exit(1);
    }
}

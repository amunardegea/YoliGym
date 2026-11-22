# Configuración del Backup Automático en GitHub (Multiusuario)

YoliGym guarda automáticamente una copia de seguridad JSON al día en un **repositorio privado de GitHub**.  
Cada dispositivo/persona usa un **identificador propio** (por ejemplo `yoli`, `andres`), de modo que varios usuarios pueden compartir el mismo repositorio privado sin pisarse los datos:

```text
backups/
├── yoli/
│   ├── yoligym_backup_yoli_2026-11-22.json
│   └── yoligym_backup_yoli_2026-11-21.json
└── andres/
    └── yoligym_backup_andres_2026-11-22.json
```

Cada usuario conserva automáticamente los **últimos 30 días** de sus propios backups y nunca borra los de otros usuarios.

---

## Paso 1: Crear el repositorio privado en GitHub (1 vez)

1. Entra en [github.com/new](https://github.com/new).
2. **Repository name:** por ejemplo `yoligym-backups`.
3. Selecciona **Private** (Privado).
4. **Muy importante:** marca la casilla **Add a README file** para que el repositorio nazca con la rama `main` inicializada.
5. Pulsa **Create repository**.

---

## Paso 2: Crear un Fine-grained Personal Access Token (1 vez)

1. En GitHub, arriba a la derecha, pulsa tu foto de perfil → **Settings**.
2. En el menú lateral izquierdo, baja hasta el final y entra en **Developer settings**.
3. Ve a **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.
4. Configura el token:
   - **Token name:** `YoliGym Backups`
   - **Expiration:** elige la duración que prefieras (por ejemplo *No expiration* o *1 year*).
   - **Repository access:** selecciona **Only select repositories** y marca únicamente `yoligym-backups`.
   - **Permissions → Repository permissions → Contents:** cambia a **Read and write** *(el permiso Metadata: Read-only se marcará automáticamente)*.
5. Pulsa **Generate token** y **copia el token** (`github_pat_...`).

*(Puedes usar el mismo token en los distintos dispositivos, o generar un token distinto por dispositivo apuntando al mismo repositorio).*

---

## Paso 3: Configurar cada dispositivo en YoliGym

1. Abre **YoliGym** en el teléfono → pestaña **Ajustes**.
2. En la tarjeta **Backup Automático en GitHub**, pulsa **Configurar Backup en GitHub**.
3. Rellena:
   - **Repositorio:** `tu-usuario/yoligym-backups` (por ejemplo `amunardegea/yoligym-backups`).
   - **Identificador de usuario / dispositivo:** un nombre corto único para esa persona o móvil (ej. `yoli` en un móvil, `andres` en otro).
   - **Personal Access Token (PAT):** pega el token copiado en el Paso 2.
   - **Rama:** déjalo vacío para usar la rama por defecto (`main`).
4. Pulsa **Verificar y Guardar**.

Al instante verificará el acceso al repositorio, subirá la primera copia diaria y dejará programado el backup automático cada 24 h.

---

## Cómo restaurar en un móvil nuevo

1. Instala YoliGym en el nuevo móvil y repite el **Paso 3** poniendo el mismo repositorio, el **mismo identificador de usuario** y el token.
2. En **Ajustes → Backup Automático en GitHub**, pulsa **Restaurar desde GitHub**.
3. Elige la fecha que quieras recuperar y confirma.

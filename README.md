# CADPRINT Manager — MVP local v0.1

Aplicación interna de inventario para CADPRINT. **Esta es una primera entrega auditable, no un sistema listo para internet.**

## Requisitos
- Java 21
- Maven 3.9+ (o IDE con Maven)
- Node.js 20+

## Ejecutar en Windows

En una terminal PowerShell, desde la raíz del proyecto:

```powershell
cd backend
$env:CADPRINT_ADMIN_PASSWORD = Read-Host 'Contraseña local para admin' -MaskInput
$env:CADPRINT_OPERATOR_PASSWORD = Read-Host 'Contraseña local para operador' -MaskInput
mvn spring-boot:run
```

`-MaskInput` requiere PowerShell 7. Si Maven no está en PATH, ejecuta el comando con la ruta completa de `mvn.cmd`. El perfil de arranque de VS Code también usa `backend` como directorio de trabajo para resolver la misma ruta H2. Maven recompila las clases y copia las migraciones antes de arrancar.

En otra terminal, desde la raíz del proyecto:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abre http://127.0.0.1:5173 e inicia sesión como `admin` u `operador` con la contraseña que configuraste. Vite reenvía `/api` a `127.0.0.1:8080`. Si necesitas una URL distinta, configura `VITE_API_BASE_URL` y, para conexiones directas al backend, `CADPRINT_ALLOWED_ORIGINS` con los orígenes exactos separados por comas.

Al ejecutar desde `backend`, H2 guarda los datos en `backend/data/cadprint.mv.db`. Flyway crea el esquema vacío y registra las migraciones versionadas. Hibernate valida las entidades al arrancar. Un esquema del MVP sin historial de Flyway requiere conservarse como respaldo o abrirse con una instalación del MVP. En una base del Sprint 2 con historial de Flyway, el arranque aplica las migraciones pendientes y conserva los datos existentes.

El perfil `mysql` utiliza `DB_URL`, `DB_USER` y `DB_PASSWORD`. Su migración está preparada para un esquema vacío y queda pendiente de verificación contra MySQL.

Los usuarios están en memoria y se autentican con HTTP Basic. El navegador conserva temporalmente las credenciales en `sessionStorage`. El servidor se vincula a la interfaz local.

## Verificar

Desde `backend`, ejecuta `mvn test`. Las pruebas usan H2 en memoria. Desde `frontend`, ejecuta `npm.cmd run format:check`, `npm.cmd run lint` y `npm.cmd run build`.
## Reglas implementadas
- Material nuevo comienza en stock cero; solo movimientos alteran existencias.
- Entradas, salidas y ajustes requieren cantidad positiva y motivo.
- No se permite dejar stock negativo.
- Cada movimiento guarda usuario, fecha, saldo anterior y saldo posterior.
- Movimiento y actualización de existencias se realizan en una transacción, con bloqueo de fila para evitar carreras.
- SKU único; el backend valida campos básicos.
- Operador puede registrar materiales, proveedores y movimientos; la eliminación está restringida (y no existen endpoints de eliminación en esta versión).

## Auditoría: pruebas manuales
1. Iniciar sesión con admin.
2. Crear material `PLA-NEGRO`, unidad `g`, costo `0.18`, mínimo `100`.
3. Registrar ENTRADA de 1000 g: saldo 1000.
4. Registrar SALIDA de 65 g: saldo 935.
5. Intentar SALIDA de 1000 g: debe rechazarla y conservar 935.
6. Intentar crear otro material con SKU `PLA-NEGRO`: debe rechazarse.
7. Consultar historial: verificar usuario, motivo y saldos.
8. Recargar navegador y reiniciar backend: los datos deben persistir.

## Auditoría manual del Sprint 2

1. En Materiales, crea una categoría, asígnala a un material y renómbrala. Comprueba que el filtro y el material muestran el nuevo nombre.
2. Edita el nombre y costo de referencia del material. Verifica que el saldo se conserve.
3. Consulta `GET /api/materials/{id}/changes` con autenticación Basic. Verifica el usuario, la fecha, el campo, el valor anterior y el nuevo. Guardar los mismos valores deja el historial igual.
4. Registra una entrada y una salida, e intenta cambiar la unidad. Comprueba el aviso, la unidad persistida y el historial de auditoría.
5. Reinicia el backend y verifica que el material, sus movimientos y su auditoría sigan disponibles.

## Imagen principal de productos

El backend guarda JPEG y PNG de hasta 5 MiB y 20 megapíxeles. Configura `CADPRINT_PRODUCT_IMAGES_DIRECTORY` con una ruta privada de escritura para el backend. Al arrancar desde `backend`, el valor predeterminado es `backend/data/product-images/`. H2 conserva los metadatos y la referencia. Respalda la base y el directorio juntos con el backend detenido.

- `GET /api/products/{id}/image` devuelve la imagen con autenticación Basic. El detalle del producto incluye `image` con tipo, tamaño y fecha, o `null`.
- `PUT /api/products/{id}/image` recibe `file` y `version` como campos multipart. ADMIN y OPERADOR pueden cargar o reemplazar la imagen.
- `DELETE /api/products/{id}/image?version=...` retira la imagen. Solo ADMIN puede hacerlo. Las operaciones de escritura devuelven la versión resultante del producto.

Un cierre inesperado puede dejar archivos sin referencia tras escribir una imagen nueva o tras confirmar un reemplazo. Para reconciliar, detén todas las instancias que usen el directorio, verifica el respaldo y compara los nombres dentro del directorio privado con las claves `product_images.storage_key` en modo de solo lectura. Conserva todos los archivos referenciados y mueve los no referenciados a cuarentena antes de eliminarlos. Si falta un archivo referenciado, restaura desde el respaldo consistente. La limpieza automática durante una transacción evita borrar archivos cuando el resultado de la confirmación es incierto.

## Estimaciones comerciales de producción (V7)

V7 guarda una estimación histórica opcional por cálculo. Los cálculos anteriores conservan sus datos y pueden carecer de estimación. La capa de persistencia conserva los valores registrados y la consulta del historial los devuelve tal como se guardaron.

Política de cálculo:

- Precio sugerido = costo unitario / (1 - margen objetivo / 100).
- Precio elegido = precio indicado por el usuario o, si se omite, precio sugerido.
- Ganancia bruta por unidad = precio elegido - costo unitario.
- Margen real = (precio elegido - costo unitario) / precio elegido × 100. Con precio elegido cero se guarda NULL.
- Usar el costo unitario histórico con ocho decimales. Mantener la precisión intermedia y redondear con HALF_UP los importes a dos decimales y el margen real a cuatro.
- Permitir precios inferiores al costo y guardar la advertencia **below_cost** comparando contra el costo histórico sin redondearlo previamente.
- Guardar cabecera, detalle, líneas y estimación en una sola transacción. Los precios comerciales del catálogo se administran por separado.

### API de costos de producción

Rutas bajo `/api/production-costs`:

- `POST /fdm/preview` y `POST /sublimation/preview` devuelven el desglose, los precios y un `previewToken`.
- `POST /fdm` y `POST /sublimation` guardan con `{ "input": { ... }, "previewToken": "..." }`. Envía la misma entrada que se utilizó en la vista previa. Si cambian los datos o las fuentes, el servidor devuelve 409. El token está ligado al usuario y a la instancia del backend. Tras un reinicio hay que recalcular las vistas previas.
- `GET /calculations` pagina el historial con `page`, `size` y el filtro opcional `technique`. `GET /calculations/{id}` consulta los valores guardados.
- `GET /profiles` y `GET /machines?technique=FDM` listan configuraciones activas. Solo ADMIN puede crear y editar perfiles y máquinas con `POST` y `PUT /{id}` en sus rutas, respectivamente. Las ediciones requieren `version`.

El objeto `common` de la vista previa incluye `jobDescription`, `expectedGoodUnits`, `costProfileId`, `machineId`, `productiveSeconds`, `materials` y `targetMarginPct`. Admite `powerWatts`, `chosenUnitPrice` y una referencia opcional mediante `productId` o `variantId`. Cada material lleva `materialId`, `quantity` y `unit`. El costo cero exige `acceptZeroCost: true` y `zeroCostReason`.

- FDM agrega `printSeconds`, `laborSeconds`, `partMassGrams` y, cuando corresponda, `supportMassGrams`. Las masas son datos técnicos. El costo de material proviene de `materials`.
- Sublimación agrega `cycleCount`, `pressSecondsPerCycle`, `preparationSeconds` y `laborSeconds`. Energía y maquinaria usan el tiempo total de prensado. `laborSeconds` registra todo el tiempo humano imputable, incluida la preparación manual si corresponde. `preparationSeconds` se conserva como dato técnico. Las cantidades de materiales son totales del trabajo.
- Las líneas horarias guardan segundos y tarifa en Q/h. Energía registra vatios-segundo y tarifa en Q/kWh. Las líneas de material guardan cantidad y unidad de entrada, factor y tarifa en la unidad de inventario.


## API de cotizaciones multiproducto

Todas las rutas usan HTTP Basic bajo `/api/quotations`. ADMIN y OPERADOR pueden consultar, crear, editar borradores, emitir, crear revisiones, aceptar, rechazar y registrar el vencimiento. Solo ADMIN puede cancelar una emisión.

| Método y ruta relativa | Entrada y salida |
| --- | --- |
| `POST /` | Recibe un borrador. Devuelve 201 con el detalle y `Location` de la revisión 1. |
| `GET /` | Pagina la revisión más reciente de cada cotización, aunque sea un borrador. Filtros opcionales `status`, `number` y `recipient`. Los dos últimos buscan coincidencias parciales sin distinguir mayúsculas. `page` empieza en 0 y `size` va de 1 a 100. Devuelve `items`, `page`, `size`, `totalElements` y `totalPages`. |
| `GET /{id}` | Devuelve el detalle de la revisión más reciente. |
| `GET /{id}/revisions/{number}` | Devuelve el detalle de la revisión indicada. |
| `GET /{id}/revisions` | Devuelve los resúmenes del más reciente al más antiguo. |
| `GET /{id}/events` | Devuelve los eventos internos de todas las revisiones en orden cronológico. |
| `PUT /{id}/revisions/{number}/draft` | Recibe `{"version": 0, "draft": {...}}`. Sustituye el contenido completo, incluidas las líneas en el orden del arreglo. Devuelve el detalle actualizado. |
| `POST /{id}/revisions/{number}/emit` | Recibe `{"version": 0}`. Devuelve el detalle emitido. Un reintento sobre la misma revisión devuelve su estado actual sin generar otro evento. |
| `POST /{id}/revisions/{number}/revise` | Recibe `{"version": 1}`. Copia la revisión emitida que indica la ruta y devuelve 201 con el nuevo borrador y su `Location`. |
| `POST /{id}/revisions/{number}/accept` y `/reject` | Admiten `{"reason":"..."}` opcional. Devuelven el detalle actualizado. |
| `POST /{id}/revisions/{number}/cancel` | Requiere ADMIN y `{"reason":"..."}` con motivo. Devuelve el detalle actualizado. |
| `POST /{id}/revisions/{number}/expire` | Registra el vencimiento de una emisión cuyo último día válido ya pasó. Devuelve el detalle actualizado. |
| `GET /{id}/revisions/{number}/commercial` | Devuelve la vista de una revisión emitida con número comercial, destinatario, emisor, vigencia, condiciones, líneas e importes. Excluye auditoría, notas internas, identificadores de cálculos, advertencias, costos y márgenes. Requiere autenticación. |

Ejemplo de borrador. El servidor calcula todos los importes.

```json
{
  "recipient": {"name": "Cliente", "email": "cliente@example.com"},
  "commercialTerms": "Entrega en tienda",
  "validUntil": null,
  "generalDiscount": {"type": "PERCENTAGE", "value": 5},
  "shippingAmount": 20.00,
  "lines": [
    {"type": "CATALOG", "variantId": 12, "quantity": 2},
    {"type": "CUSTOM", "name": "Diseño", "description": "Diseño personalizado",
     "quantity": 1, "unitPrice": 50.00, "discount": {"type": "FIXED", "value": 5.00}}
  ]
}
```

- Una línea de catálogo requiere `productId` o `variantId`. Omitir `unitPrice` usa el precio propio de la variante o el precio base disponible. Una línea personalizada requiere descripción y precio explícito. Se admite precio cero. Cada borrador admite hasta 200 líneas.
- `productionCalculationId` es opcional. Un cálculo con variante debe coincidir con la variante cotizada y uno con producto debe coincidir con el producto. Los cálculos sin referencia de catálogo se admiten en líneas personalizadas. La advertencia interna compara el neto de línea con el costo histórico multiplicado por la cantidad cotizada, sin distribuir el descuento general. Es orientativa porque el cálculo puede corresponder a otro volumen.
- La política `v1` usa GTQ, cantidades enteras positivas e importes con dos decimales. `NONE` omite el valor, `FIXED` usa GTQ y `PERCENTAGE` admite de 0 a 100 con hasta cuatro decimales. Se redondea cada descuento a centavos con HALF_UP y se resta de su base. El descuento general se aplica después de los descuentos de línea. El envío se suma al final.
- Al emitir se asigna `COT-` seguido del ID de la cotización con un mínimo de seis dígitos. El orden depende de la creación de la cotización y puede haber saltos. Las revisiones mantienen el mismo número. La fecha predeterminada es el día de emisión en `America/Guatemala` más 15 días calendario, inclusive como último día válido. Una revisión copiada requiere su propia vigencia, predeterminada al emitir si se deja en blanco.
- Configura la instantánea del emisor con `cadprint.quotations.issuer-name` (predeterminado `CADPRINT`) y, opcionalmente, `issuer-email`, `issuer-phone` e `issuer-address` bajo el mismo prefijo. El detalle interno contiene `revision.version`, `recipient`, `issuer`, `totals` y `lines`. Usa la versión devuelta más reciente al editar, emitir o crear una revisión.
- La emisión vigente conserva su validez hasta que se emite otra revisión. Aceptarla cierra el ciclo y bloquea la edición y emisión de cualquier borrador existente, que sigue disponible en el historial. El vencimiento impide aceptar la revisión desde el primer día posterior a `validUntil`, incluso antes de registrar el estado mediante `/expire`.
- Los errores de validación devuelven 400, los permisos insuficientes 403, los recursos inexistentes 404 y las versiones obsoletas o transiciones incompatibles 409. Los errores de aplicación devuelven `{"error":"..."}`.

### PDF histórico de cotizaciones

La emisión registra un trabajo documental en la misma transacción. El trabajador lo procesa después del commit y reintenta fallos transitorios. Las revisiones emitidas antes de V9 pueden solicitar un documento bajo demanda.

- Configura `CADPRINT_QUOTATION_DOCUMENTS_DIRECTORY` como una ruta absoluta privada fuera del proyecto y de los recursos estáticos. Predeterminado `${user.home}/.cadprint/quotation-documents`. Restringe el volumen a la cuenta del servicio e inclúyelo en el respaldo junto con la base.
- Las rutas están bajo `/api/quotations/{id}/revisions/{number}/document`. `POST` solicita la generación o reintenta un fallo definitivo. `GET` consulta estado y metadatos. `GET /download` devuelve el original autenticado como archivo adjunto.
- Un documento `READY` conserva sus bytes y SHA-256. Una descarga con archivo ausente o alterado devuelve 503. Restaura el original desde el respaldo. La recuperación automática se limita a trabajos pendientes o abandonados.
- Para ejecutar sin el trabajador, configura `cadprint.quotation-documents.worker-enabled=false`. El intervalo predeterminado es de cinco segundos y se ajusta con `cadprint.quotation-documents.poll-ms`. Varias instancias deben compartir el volumen.

## Limitaciones conocidas / pendientes
- El inventario ofrece edición, búsqueda y filtros. La paginación, importación, exportación, lotes, imágenes y compras quedan pendientes.
- El costo unitario de referencia se introduce manualmente. Multiplicado por el stock, produce la valoración estimada del inventario. El costo de compra por movimiento queda pendiente.
- La precisión de cantidad se limita a tres decimales; precio unitario a cuatro.
- La pantalla muestra los 100 movimientos más recientes.
- Para despliegue público se requieren autenticación por sesión segura o tokens, HTTPS, respaldos, auditoría ampliada, permisos por operación y pruebas de seguridad.

## Estructura
`backend/` API REST Spring Boot, `frontend/` React + TypeScript + Vite.
#   C A D P R I N T _ M A N A G E R  
 
# Instancia local con MySQL 8.4

Requiere Docker con Compose. Desde la raíz del repositorio, copia
`deploy/.env.example` a `.env` y asigna cuatro contraseñas distintas.
Inicia la aplicación con:

```sh
docker compose up --build -d
```

Abre http://127.0.0.1:8088 e inicia sesión como `admin` u `operador`.
El puerto se puede cambiar con `CADPRINT_WEB_PORT`. MySQL, documentos
PDF e imágenes se conservan en volúmenes separados al reiniciar los
contenedores. Usa `docker compose down` para detener la instancia.

El navegador y el backend comparten el mismo origen a través de Nginx.
Solo se publica la interfaz web en la dirección local de la máquina.
Flyway inicializa una base MySQL independiente. Para usar datos previos
de H2 se necesitará una migración de datos separada.

La prueba de despliegue de GitHub Actions levanta una instancia temporal,
verifica ambos roles y recorre el flujo de cotización, pedido y cobro.

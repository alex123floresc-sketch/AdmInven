-- V4: pedidos de la tienda en línea.

CREATE TABLE pedido (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha          TEXT    NOT NULL,
    cliente        TEXT    NOT NULL,
    telefono       TEXT    NOT NULL,
    nota           TEXT    NOT NULL DEFAULT '',
    estado         TEXT    NOT NULL CHECK (estado IN ('PENDIENTE', 'ATENDIDO', 'CANCELADO')),
    atendido_por   TEXT    NOT NULL DEFAULT '',
    fecha_atencion TEXT
);

-- Nombre y precio tal como los vio el cliente al pedir.
CREATE TABLE pedido_linea (
    pedido_id       INTEGER NOT NULL REFERENCES pedido (id),
    orden           INTEGER NOT NULL,
    codigo_producto TEXT    NOT NULL,
    nombre          TEXT    NOT NULL,
    cantidad        INTEGER NOT NULL CHECK (cantidad > 0),
    precio          TEXT    NOT NULL,
    PRIMARY KEY (pedido_id, orden)
);

CREATE INDEX idx_pedido_estado ON pedido (estado);

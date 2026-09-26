-- V1: esquema inicial (Fase 3). Los importes se guardan como TEXT para no perder decimales de BigDecimal.

CREATE TABLE producto (
    codigo       TEXT    PRIMARY KEY,
    nombre       TEXT    NOT NULL,
    categoria    TEXT    NOT NULL,
    precio       TEXT    NOT NULL,
    stock        INTEGER NOT NULL CHECK (stock >= 0),
    stock_minimo INTEGER NOT NULL CHECK (stock_minimo >= 0),
    activo       INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0, 1))
);

CREATE TABLE movimiento (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha            TEXT    NOT NULL,
    codigo_producto  TEXT    NOT NULL REFERENCES producto (codigo),
    tipo             TEXT    NOT NULL CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    cantidad         INTEGER NOT NULL,
    stock_resultante INTEGER NOT NULL,
    nota             TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX idx_movimiento_producto ON movimiento (codigo_producto);
CREATE INDEX idx_movimiento_fecha ON movimiento (fecha);

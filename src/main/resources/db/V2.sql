-- V2 (Fase 5): costos, proveedores e importes en los movimientos.

ALTER TABLE producto ADD COLUMN costo TEXT NOT NULL DEFAULT '0.00';

CREATE TABLE proveedor (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre    TEXT    NOT NULL,
    documento TEXT    NOT NULL DEFAULT '',
    telefono  TEXT    NOT NULL DEFAULT '',
    email     TEXT    NOT NULL DEFAULT '',
    activo    INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0, 1))
);

CREATE UNIQUE INDEX idx_proveedor_nombre ON proveedor (nombre COLLATE NOCASE);

ALTER TABLE movimiento ADD COLUMN precio_unitario TEXT NOT NULL DEFAULT '0.00';
ALTER TABLE movimiento ADD COLUMN costo_unitario TEXT NOT NULL DEFAULT '0.00';
ALTER TABLE movimiento ADD COLUMN proveedor_id INTEGER REFERENCES proveedor (id);

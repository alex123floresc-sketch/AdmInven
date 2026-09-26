-- V3 (Fase 6): usuarios y auditoría de movimientos.

CREATE TABLE usuario (
    nombre_usuario  TEXT    PRIMARY KEY,
    nombre_completo TEXT    NOT NULL,
    rol             TEXT    NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'VENDEDOR')),
    contrasena      TEXT    NOT NULL,
    activo          INTEGER NOT NULL DEFAULT 1 CHECK (activo IN (0, 1))
);

-- Quién registró cada movimiento; vacío en los movimientos anteriores a esta versión.
ALTER TABLE movimiento ADD COLUMN usuario TEXT NOT NULL DEFAULT '';

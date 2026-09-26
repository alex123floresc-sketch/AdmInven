package inventario;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Reloj que se puede mover a mano; permite generar movimientos con fechas pasadas. */
final class RelojAjustable extends Clock {

    private final ZoneId zona;
    private Instant ahora;

    RelojAjustable(LocalDateTime inicio, ZoneId zona) {
        this.zona = zona;
        fijar(inicio);
    }

    void fijar(LocalDateTime fecha) {
        ahora = fecha.atZone(zona).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojAjustable(LocalDateTime.ofInstant(ahora, zona), otraZona);
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}

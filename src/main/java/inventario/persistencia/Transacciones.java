package inventario.persistencia;

import java.util.function.Supplier;

/**
 * Ejecuta varias operaciones de repositorio como una unidad: o se guardan todas o ninguna.
 */
public interface Transacciones {

    /** Para almacenamientos sin soporte de transacciones: ejecuta la operación tal cual. */
    Transacciones NINGUNA = new Transacciones() {
        @Override
        public <T> T ejecutar(Supplier<T> operacion) {
            return operacion.get();
        }
    };

    <T> T ejecutar(Supplier<T> operacion);

    default void ejecutar(Runnable operacion) {
        ejecutar(() -> {
            operacion.run();
            return null;
        });
    }
}

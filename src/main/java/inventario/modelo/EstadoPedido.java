package inventario.modelo;

/** Un pedido de la tienda en línea empieza pendiente y el personal lo atiende (se vende) o lo cancela. */
public enum EstadoPedido {
    PENDIENTE("Pendiente"),
    ATENDIDO("Atendido"),
    CANCELADO("Cancelado");

    private final String nombre;

    EstadoPedido(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}

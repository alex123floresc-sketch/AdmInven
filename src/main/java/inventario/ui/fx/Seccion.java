package inventario.ui.fx;

import javafx.scene.Node;

/** Una pestaña de la ventana principal. */
interface Seccion {

    String titulo();

    Node vista();

    /** Vuelve a leer los datos del servicio (se llama al mostrarla y tras cualquier cambio). */
    void refrescar();
}

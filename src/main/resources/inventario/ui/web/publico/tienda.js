'use strict';
/*
 * Tienda para clientes: catálogo público (sin costos ni stock exacto) y envío de pedidos para recoger en tienda.
 * El servidor vuelve a validar cada pedido (productos activos, stock y datos de contacto).
 */

const tienda = {
    productos: [],
    categoria: '',
    /** Líneas del pedido en preparación: [{codigo, cantidad}]; se recuerdan en este navegador. */
    pedido: leerPedido(),
};

const MAXIMO_POR_PRODUCTO = 999;
const DISPONIBILIDAD = {DISPONIBLE: 'Disponible', POCAS: 'Últimas unidades', AGOTADO: 'Agotado'};

function leerPedido() {
    try {
        const guardado = JSON.parse(localStorage.getItem('pedido') ?? '[]');
        return Array.isArray(guardado) ? guardado : [];
    } catch {
        return [];
    }
}

function guardarPedido() {
    try {
        localStorage.setItem('pedido', JSON.stringify(tienda.pedido));
    } catch {
        // Sin almacenamiento: el pedido se conserva mientras la página esté abierta.
    }
}

const producto = codigo => tienda.productos.find(p => p.codigo === codigo);
const cantidadEnPedido = codigo => tienda.pedido.find(l => l.codigo === codigo)?.cantidad ?? 0;
const totalPedido = () => tienda.pedido.reduce((s, l) => s + Number(producto(l.codigo).precio) * l.cantidad, 0);

/** Tono de la tarjeta según la categoría, para distinguirlas de un vistazo (las clases están en estilos.css). */
function tono(texto) {
    let h = 0;
    for (const c of texto) {
        h = (h * 31 + c.codePointAt(0)) % 997;
    }
    return 'tono-' + (h % 6);
}

const iniciales = nombre => nombre.split(/\s+/).filter(p => /^\p{L}/u.test(p)).slice(0, 2)
    .map(p => p[0].toUpperCase()).join('');

async function iniciarTienda() {
    iniciarTema();
    $('#carrito-icono').innerHTML = icono('caja');
    $('#boton-carrito').onclick = abrirCarrito;
    $('#cerrar-carrito').onclick = () => $('#carrito').close();
    $('#buscar').oninput = dibujarCatalogo;
    $('#orden').onchange = dibujarCatalogo;
    $('#catalogo').onclick = e => {
        const b = e.target.closest('button[data-accion]');
        if (b) {
            cambiarCantidad(b.dataset.codigo, b.dataset.accion === 'menos' ? -1 : 1);
        }
    };
    $('#carrito-lineas').onclick = e => {
        const b = e.target.closest('button[data-accion]');
        if (b) {
            const cambio = {menos: -1, mas: 1, quitar: -cantidadEnPedido(b.dataset.codigo)}[b.dataset.accion];
            cambiarCantidad(b.dataset.codigo, cambio);
        }
    };
    $('#form-pedido').onsubmit = enviarPedido;
    try {
        tienda.productos = await llamarApi('GET', '/api/catalogo');
    } catch (e) {
        $('#estado-catalogo').textContent = e.message;
        return;
    }
    // Lo que se dejó en el pedido otra vez y ya no está a la venta se quita.
    tienda.pedido = tienda.pedido.filter(l => producto(l.codigo) && producto(l.codigo).disponibilidad !== 'AGOTADO');
    dibujarCategorias();
    dibujarCatalogo();
    actualizarContador();
}

function dibujarCategorias() {
    const categorias = [...new Set(tienda.productos.map(p => p.categoria))].sort((a, b) => a.localeCompare(b, 'es'));
    $('#categorias').innerHTML = ['', ...categorias].map(c => `<button type="button" class="chip" data-categoria="${esc(c)}"
        aria-pressed="${c === tienda.categoria}">${esc(c || 'Todo')}</button>`).join('');
    $$('#categorias .chip').forEach(b => b.onclick = () => {
        tienda.categoria = b.dataset.categoria;
        $$('#categorias .chip').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
        dibujarCatalogo();
    });
}

function dibujarCatalogo() {
    const t = $('#buscar').value.trim().toLowerCase();
    const orden = $('#orden').value;
    const visibles = tienda.productos
        .filter(p => (!tienda.categoria || p.categoria === tienda.categoria)
            && (!t || p.nombre.toLowerCase().includes(t) || p.categoria.toLowerCase().includes(t)))
        .sort((a, b) => orden === 'nombre' ? a.nombre.localeCompare(b.nombre, 'es')
            : (Number(a.precio) - Number(b.precio)) * (orden === 'menor' ? 1 : -1));
    $('#estado-catalogo').textContent = visibles.length === 0 ? 'No encontramos productos con esa búsqueda.'
        : `${entero(visibles.length)} producto(s)`;
    $('#catalogo').innerHTML = visibles.map(p => {
        const c = esc(p.codigo);
        const cantidad = cantidadEnPedido(p.codigo);
        const agotado = p.disponibilidad === 'AGOTADO';
        const accion = agotado ? '<button type="button" class="boton ancho" disabled>Agotado</button>'
            : cantidad === 0
                ? `<button type="button" class="boton primario ancho" data-accion="mas" data-codigo="${c}">Agregar</button>`
                : `<div class="selector-cantidad">
                    <button type="button" class="boton" data-accion="menos" data-codigo="${c}" aria-label="Quitar una unidad">−</button>
                    <span>${entero(cantidad)}<span class="texto-largo"> en el pedido</span></span>
                    <button type="button" class="boton" data-accion="mas" data-codigo="${c}" aria-label="Sumar una unidad">+</button>
                </div>`;
        return `<article class="producto-tienda${agotado ? ' agotado' : ''}">
            <div class="producto-imagen ${tono(p.categoria)}" aria-hidden="true">${esc(iniciales(p.nombre))}</div>
            <div class="producto-datos">
                <span class="producto-categoria">${esc(p.categoria)}</span>
                <h3>${esc(p.nombre)}</h3>
                <div class="producto-pie"><strong class="precio">${moneda(p.precio)}</strong>
                    <span class="disponibilidad disp-${p.disponibilidad}">${DISPONIBILIDAD[p.disponibilidad]}</span></div>
            </div>
            ${accion}
        </article>`;
    }).join('');
}

function cambiarCantidad(codigo, cambio) {
    const linea = tienda.pedido.find(l => l.codigo === codigo);
    const nueva = Math.min(MAXIMO_POR_PRODUCTO, (linea?.cantidad ?? 0) + cambio);
    if (linea) {
        linea.cantidad = nueva;
    } else if (nueva > 0) {
        tienda.pedido.push({codigo, cantidad: nueva});
    }
    if (cambio > 0) {
        // El botón del pedido da un pequeño salto para que se note dónde quedó el producto.
        const boton = $('#boton-carrito');
        boton.classList.remove('salto');
        void boton.offsetWidth;
        boton.classList.add('salto');
    }
    tienda.pedido = tienda.pedido.filter(l => l.cantidad > 0);
    guardarPedido();
    actualizarContador();
    dibujarCatalogo();
    if ($('#carrito').open) {
        dibujarCarrito();
    }
}

function actualizarContador() {
    const unidades = tienda.pedido.reduce((s, l) => s + l.cantidad, 0);
    const contador = $('#carrito-cantidad');
    contador.hidden = unidades === 0;
    contador.textContent = entero(unidades);
    $('#boton-carrito').setAttribute('aria-label', `Mi pedido: ${unidades} unidad(es)`);
}

function abrirCarrito() {
    $('#carrito-error').hidden = true;
    dibujarCarrito();
    $('#carrito').showModal();
}

function dibujarCarrito() {
    const vacio = tienda.pedido.length === 0;
    $('#carrito-lineas').innerHTML = vacio
        ? '<p class="suave vacio-ticket">Su pedido está vacío. Agregue productos del catálogo.</p>'
        : tienda.pedido.map(l => {
            const p = producto(l.codigo);
            const c = esc(l.codigo);
            return `<div class="linea-ticket">
                <div class="linea-nombre"><strong>${esc(p.nombre)}</strong>
                    <span class="suave">${moneda(p.precio)} c/u</span></div>
                <div class="linea-cantidad">
                    <button type="button" class="boton chico" data-accion="menos" data-codigo="${c}" aria-label="Quitar una unidad">−</button>
                    <span>${entero(l.cantidad)}</span>
                    <button type="button" class="boton chico" data-accion="mas" data-codigo="${c}" aria-label="Sumar una unidad">+</button>
                </div>
                <strong class="num">${moneda(Number(p.precio) * l.cantidad)}</strong>
                <button type="button" class="boton chico peligro" data-accion="quitar" data-codigo="${c}"
                        aria-label="Quitar ${esc(p.nombre)}">×</button>
            </div>`;
        }).join('');
    $('#carrito-total').textContent = moneda(totalPedido());
    $('#carrito .total-ticket').hidden = vacio;
    $('#carrito-datos').hidden = vacio;
}

async function enviarPedido(evento) {
    evento.preventDefault();
    const datos = Object.fromEntries(new FormData(evento.target));
    const boton = $('#enviar-pedido');
    const error = $('#carrito-error');
    boton.disabled = true;
    error.hidden = true;
    try {
        const recibido = await llamarApi('POST', '/api/pedidos', {
            cliente: datos.cliente, telefono: datos.telefono, nota: datos.nota,
            lineas: tienda.pedido.map(l => ({codigo: l.codigo, cantidad: String(l.cantidad)})),
        });
        tienda.pedido = [];
        guardarPedido();
        actualizarContador();
        dibujarCatalogo();
        evento.target.reset();
        $('#carrito-lineas').innerHTML = `<div class="pedido-enviado">
            <h3>¡Pedido N.° ${entero(recibido.numero)} recibido!</h3>
            <p>Total: <strong>${moneda(recibido.total)}</strong></p>
            <p class="suave">Le llamaremos al ${esc(datos.telefono)} para confirmar cuándo puede recogerlo.
                Guarde su número de pedido.</p>
        </div>`;
        $('#carrito .total-ticket').hidden = true;
        $('#carrito-datos').hidden = true;
    } catch (e) {
        error.textContent = e.message;
        error.hidden = false;
    } finally {
        boton.disabled = false;
    }
}

iniciarTienda();

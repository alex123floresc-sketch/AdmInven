'use strict';
/*
 * Página del Administrador de Inventario. Habla con la API del servidor (/api); las reglas de negocio
 * y los permisos se verifican en el servidor, aquí solo se ocultan las acciones que el rol no permite.
 */

// ---------- Utilidades ----------

const $ = (selector, raiz = document) => raiz.querySelector(selector);
const $$ = (selector, raiz = document) => [...raiz.querySelectorAll(selector)];

/** Escapa texto para insertarlo en HTML (evita que un nombre de producto inyecte código). */
function esc(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c]);
}

const formatoImporte = new Intl.NumberFormat('es-PE', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const formatoEntero = new Intl.NumberFormat('es-PE');
const numero = v => v == null ? '—' : formatoImporte.format(Number(v));
const moneda = v => v == null ? '—' : 'S/ ' + formatoImporte.format(Number(v));
const entero = v => formatoEntero.format(Number(v));
const formatoPorcentaje = new Intl.NumberFormat('es-PE', {minimumFractionDigits: 1, maximumFractionDigits: 1});
const porcentaje = v => v == null ? '—' : formatoPorcentaje.format(Number(v)) + ' %';

function fechaHora(iso) {
    const [fecha, hora] = iso.split('T');
    return fecha.split('-').reverse().join('/') + ' ' + hora.slice(0, 5);
}

const fechaCorta = iso => iso ? iso.split('-').reverse().join('/') : '—';

function hoyIso(desplazamientoDias = 0) {
    const d = new Date();
    d.setDate(d.getDate() + desplazamientoDias);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const NOMBRES_TIPO = {ENTRADA: 'Compra', SALIDA: 'Venta', AJUSTE: 'Ajuste'};

// ---------- Comunicación con el servidor ----------

class ErrorApi extends Error {
    constructor(mensaje, estado) {
        super(mensaje);
        this.estado = estado;
    }
}

async function api(metodo, url, cuerpo) {
    const opciones = {method: metodo, headers: {'X-Inventario': '1'}, credentials: 'same-origin'};
    if (cuerpo !== undefined) {
        opciones.headers['Content-Type'] = 'application/json';
        opciones.body = JSON.stringify(cuerpo);
    }
    let respuesta;
    try {
        respuesta = await fetch(url, opciones);
    } catch {
        throw new ErrorApi('No se pudo conectar con el servidor. Revise su conexión.', 0);
    }
    if (respuesta.status === 401 && url !== '/api/sesion') {
        estado.usuario = null;
        mostrarAcceso(false);
        throw new ErrorApi('Su sesión terminó. Vuelva a iniciar sesión.', 401);
    }
    if (respuesta.status === 204) {
        return null;
    }
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
        throw new ErrorApi(datos?.error ?? `Error ${respuesta.status}`, respuesta.status);
    }
    return datos;
}

// ---------- Estado general ----------

const estado = {
    usuario: null,
    demo: false,
    credencialesDemo: null,
    pestana: 'resumen',
};

const puede = permiso => estado.usuario?.permisos.includes(permiso) ?? false;

function aviso(mensaje, error = false) {
    const el = document.createElement('div');
    el.className = 'aviso' + (error ? ' error' : '');
    el.textContent = mensaje;
    $('#avisos').append(el);
    setTimeout(() => el.remove(), error ? 6000 : 3500);
}

/** Ejecuta una acción y muestra su error como aviso (para botones fuera de diálogos). */
async function intentar(accion) {
    try {
        await accion();
    } catch (e) {
        if (e.estado !== 401) {
            aviso(e.message, true);
        }
    }
}

// ---------- Diálogo genérico ----------

/**
 * Abre un formulario. {@code alAceptar} recibe los valores del formulario; si lanza un error,
 * el mensaje se muestra dentro del diálogo y este sigue abierto para corregir los datos.
 */
function dialogo({titulo, cuerpo, textoAceptar = 'Guardar', alAceptar, ancho = false, soloCerrar = false,
                     alAbrir}) {
    const dlg = $('#dialogo');
    const form = $('#dialogo-form');
    const error = $('#dialogo-error');
    const aceptar = $('#dialogo-aceptar');
    const cancelar = $('#dialogo-cancelar');
    dlg.classList.toggle('ancho', ancho);
    $('#dialogo-titulo').textContent = titulo;
    $('#dialogo-cuerpo').innerHTML = cuerpo;
    error.hidden = true;
    aceptar.hidden = soloCerrar;
    aceptar.textContent = textoAceptar;
    aceptar.disabled = false;
    cancelar.textContent = soloCerrar ? 'Cerrar' : 'Cancelar';

    return new Promise(resolver => {
        const cerrar = resultado => {
            form.onsubmit = null;
            cancelar.onclick = null;
            dlg.onclose = null;
            if (dlg.open) {
                dlg.close();
            }
            resolver(resultado);
        };
        form.onsubmit = async evento => {
            evento.preventDefault();
            aceptar.disabled = true;
            try {
                const valores = Object.fromEntries(new FormData(form));
                cerrar(await alAceptar(valores) ?? true);
            } catch (e) {
                if (e.estado === 401) {
                    cerrar(null);
                    return;
                }
                error.textContent = e.message;
                error.hidden = false;
                aceptar.disabled = false;
            }
        };
        cancelar.onclick = () => cerrar(null);
        dlg.onclose = () => cerrar(null);
        dlg.showModal();
        const primero = $('#dialogo-cuerpo input:not([type=hidden]):not([readonly]), #dialogo-cuerpo select');
        (primero ?? cancelar).focus();
        alAbrir?.($('#dialogo-cuerpo'));
    });
}

function confirmar(titulo, mensaje, textoAceptar) {
    return dialogo({titulo, cuerpo: `<p>${esc(mensaje)}</p>`, textoAceptar, alAceptar: () => true});
}

const campo = (etiqueta, nombre, {tipo = 'text', valor = '', ayuda = '', completo = false, extra = ''} = {}) => `
    <label class="campo${completo ? ' completo' : ''}">${esc(etiqueta)}
        <input type="${tipo}" name="${nombre}" value="${esc(valor)}" ${extra}>
        ${ayuda ? `<span class="ayuda">${esc(ayuda)}</span>` : ''}
    </label>`;

// ---------- Tablas ----------

/**
 * Tabla ordenable. {@code columnas}: [{titulo, valor(fila), html?(fila), num?, orden?}].
 * El HTML de cada celda ya viene escapado por quien define la columna.
 */
function tabla(contenedor, columnas, filas, {vacio = 'Sin datos.', claseFila, orden} = {}) {
    let criterio = orden ?? null;
    const dibujar = () => {
        let datos = [...filas];
        if (criterio) {
            const col = columnas[criterio.indice];
            datos.sort((a, b) => {
                const x = col.valor(a), y = col.valor(b);
                const r = typeof x === 'number' && typeof y === 'number'
                    ? x - y : String(x ?? '').localeCompare(String(y ?? ''), 'es');
                return criterio.asc ? r : -r;
            });
        }
        const cabecera = columnas.map((c, i) => {
            const flecha = criterio?.indice === i ? (criterio.asc ? ' ▲' : ' ▼') : '';
            return `<th class="${c.num ? 'num' : ''} ${c.valor ? 'ordenable' : ''}" data-i="${i}">${esc(c.titulo)}${flecha}</th>`;
        }).join('');
        const cuerpo = datos.length === 0
            ? `<tr><td class="vacio" colspan="${columnas.length}">${esc(vacio)}</td></tr>`
            : datos.map(f => `<tr class="${claseFila?.(f) ?? ''}">${columnas.map(c =>
                `<td class="${c.num ? 'num' : ''} ${c.clase ?? ''}">${c.html ? c.html(f) : esc(c.valor(f))}</td>`
            ).join('')}</tr>`).join('');
        contenedor.innerHTML = `<table><thead><tr>${cabecera}</tr></thead><tbody>${cuerpo}</tbody></table>`;
        $$('th.ordenable', contenedor).forEach(th => th.onclick = () => {
            const i = Number(th.dataset.i);
            criterio = {indice: i, asc: criterio?.indice === i ? !criterio.asc : true};
            dibujar();
        });
    };
    dibujar();
}

const colMovimientos = (conProducto = true) => [
    {titulo: 'Fecha', valor: m => m.fecha, html: m => esc(fechaHora(m.fecha))},
    ...(conProducto ? [{titulo: 'Producto', valor: m => m.producto}] : []),
    {titulo: 'Tipo', valor: m => m.tipo,
        html: m => `<span class="etiqueta-tipo tipo-${m.tipo}">${NOMBRES_TIPO[m.tipo]}</span>`},
    {titulo: 'Cantidad', valor: m => m.cantidad, num: true,
        html: m => (m.cantidad > 0 ? '+' : '') + entero(m.cantidad)},
    {titulo: 'Importe', valor: m => Number(m.importe ?? 0), num: true, html: m => m.importe == null ? '' : numero(m.importe)},
    {titulo: 'Stock', valor: m => m.stockResultante, num: true, html: m => entero(m.stockResultante)},
];

// ---------- Acceso ----------

async function iniciar() {
    $('#boton-salir').onclick = () => intentar(async () => {
        await api('DELETE', '/api/sesion');
        estado.usuario = null;
        mostrarAcceso(false);
    });
    $('#boton-contrasena').onclick = cambiarMiContrasena;
    try {
        const e = await api('GET', '/api/estado');
        estado.demo = e.demo;
        estado.credencialesDemo = e.credencialesDemo;
        if (e.usuario) {
            entrar(e.usuario);
        } else {
            mostrarAcceso(e.requiereConfiguracion);
        }
    } catch (e) {
        document.body.textContent = e.message;
    }
}

function mostrarAcceso(configuracionInicial) {
    $('#app').hidden = true;
    $('#acceso').hidden = false;
    $('#dialogo').open && $('#dialogo').close();
    const error = $('#acceso-error');
    error.hidden = true;
    $('#acceso-subtitulo').textContent = configuracionInicial
        ? 'Primera ejecución: cree el usuario administrador.' : 'Inicie sesión para continuar.';
    $('#acceso-boton').textContent = configuracionInicial ? 'Crear administrador' : 'Entrar';
    $('#campos-acceso').innerHTML = configuracionInicial
        ? campo('Usuario', 'usuario', {valor: 'admin', extra: 'required autocomplete="username"'})
        + campo('Nombre completo', 'nombre', {extra: 'required placeholder="Ej. Ana Torres"'})
        + campo('Contraseña', 'contrasena', {tipo: 'password', ayuda: 'Al menos 8 caracteres, con letras y números.',
            extra: 'required autocomplete="new-password"'})
        + campo('Repetir contraseña', 'repetir', {tipo: 'password', extra: 'required autocomplete="new-password"'})
        : campo('Usuario', 'usuario', {extra: 'required autocomplete="username" autocapitalize="none"'})
        + campo('Contraseña', 'contrasena', {tipo: 'password', extra: 'required autocomplete="current-password"'});
    const demo = $('#acceso-demo');
    demo.hidden = !estado.demo || configuracionInicial;
    demo.textContent = 'Usuarios de demostración: ' + (estado.credencialesDemo ?? '');
    $('#campos-acceso input').focus();

    $('#form-acceso').onsubmit = async evento => {
        evento.preventDefault();
        const v = Object.fromEntries(new FormData(evento.target));
        const boton = $('#acceso-boton');
        boton.disabled = true;
        try {
            let usuario;
            if (configuracionInicial) {
                if (v.contrasena !== v.repetir) {
                    throw new Error('Las contraseñas no coinciden.');
                }
                usuario = await api('POST', '/api/configuracion-inicial',
                    {usuario: v.usuario, nombre: v.nombre, contrasena: v.contrasena});
            } else {
                usuario = await api('POST', '/api/sesion', {usuario: v.usuario, contrasena: v.contrasena});
            }
            entrar(usuario);
        } catch (e) {
            error.textContent = e.message;
            error.hidden = false;
            const clave = $('input[name=contrasena]');
            clave.value = '';
            clave.focus();
        } finally {
            boton.disabled = false;
        }
    };
}

function entrar(usuario) {
    estado.usuario = usuario;
    $('#acceso').hidden = true;
    $('#app').hidden = false;
    $('#usuario-nombre').textContent = usuario.nombre;
    $('#usuario-rol').textContent = usuario.rol;
    dibujarPestanas();
    const guardada = location.hash.slice(1);
    irA(PESTANAS.some(p => p.id === guardada && p.visible()) ? guardada : 'resumen');
}

async function cambiarMiContrasena() {
    const hecho = await dialogo({
        titulo: 'Cambiar contraseña',
        cuerpo: campo('Contraseña actual', 'actual', {tipo: 'password', extra: 'required autocomplete="current-password"'})
            + campo('Nueva contraseña', 'nueva', {tipo: 'password', ayuda: 'Mínimo 8 caracteres, letras y números.',
                extra: 'required autocomplete="new-password"'})
            + campo('Repetir nueva', 'repetir', {tipo: 'password', extra: 'required autocomplete="new-password"'}),
        alAceptar: async v => {
            if (v.nueva !== v.repetir) {
                throw new Error('Las contraseñas nuevas no coinciden.');
            }
            await api('POST', '/api/sesion/contrasena', {actual: v.actual, nueva: v.nueva});
        },
    });
    if (hecho) {
        aviso('Su contraseña se actualizó.');
    }
}

// ---------- Pestañas ----------

const PESTANAS = [
    {id: 'resumen', titulo: 'Resumen', visible: () => true, dibujar: dibujarResumen},
    {id: 'productos', titulo: 'Productos', visible: () => true, dibujar: dibujarProductos},
    {id: 'movimientos', titulo: 'Movimientos', visible: () => true, dibujar: dibujarMovimientos},
    {id: 'proveedores', titulo: 'Proveedores', visible: () => puede('GESTIONAR_PROVEEDORES'), dibujar: dibujarProveedores},
    {id: 'reportes', titulo: 'Reportes', visible: () => puede('VER_REPORTES'), dibujar: dibujarReportes},
    {id: 'usuarios', titulo: 'Usuarios', visible: () => puede('GESTIONAR_USUARIOS'), dibujar: dibujarUsuarios},
];

function dibujarPestanas() {
    $('#pestanas').innerHTML = PESTANAS.filter(p => p.visible()).map(p =>
        `<button type="button" role="tab" data-id="${p.id}">${p.titulo}</button>`).join('');
    $$('#pestanas button').forEach(b => b.onclick = () => irA(b.dataset.id));
}

function irA(id) {
    estado.pestana = id;
    history.replaceState(null, '', '#' + id);
    $$('#pestanas button').forEach(b => b.setAttribute('aria-selected', String(b.dataset.id === id)));
    refrescar();
}

/** Vuelve a dibujar la pestaña actual con datos frescos del servidor. */
function refrescar() {
    const pestana = PESTANAS.find(p => p.id === estado.pestana);
    intentar(() => pestana.dibujar($('#contenido')));
}

// ---------- Resumen ----------

async function dibujarResumen(raiz) {
    const r = await api('GET', '/api/resumen');
    const tarjeta = (etiqueta, valor, detalle = '', alerta = false) => `
        <div class="tarjeta${alerta ? ' alerta' : ''}">
            <div class="etiqueta">${esc(etiqueta)}</div>
            <div class="valor">${esc(valor)}</div>
            ${detalle ? `<div class="detalle">${esc(detalle)}</div>` : ''}
        </div>`;
    const finanzas = r.ventasHoy !== undefined;
    raiz.innerHTML = `
        <section class="tarjetas">
            ${tarjeta('Productos activos', entero(r.productosActivos))}
            ${tarjeta('Unidades en stock', entero(r.unidades))}
            ${finanzas ? tarjeta('Inventario al costo', moneda(r.inventarioAlCosto),
                'A precio de venta: ' + moneda(r.inventarioAPrecioDeVenta)) : ''}
            ${finanzas ? tarjeta('Ventas de hoy', moneda(r.ventasHoy), 'Ganancia: ' + moneda(r.gananciaHoy)) : ''}
            ${tarjeta('Con stock bajo', entero(r.stockBajo.length),
                r.stockBajo.length ? 'Revise la lista para reponer' : 'Todo en orden', r.stockBajo.length > 0)}
        </section>
        <section class="paneles">
            <div class="panel"><h3>Para reponer</h3><div id="tabla-reponer" class="tabla-contenedor"></div></div>
            <div class="panel">
                <div class="panel-cabecera"><h3>Últimos movimientos</h3>
                    <button class="boton chico" type="button" id="ver-movimientos">Ver todos</button></div>
                <div id="tabla-recientes" class="tabla-contenedor"></div>
            </div>
        </section>`;
    tabla($('#tabla-reponer', raiz), [
        {titulo: 'Producto', valor: p => p.nombre,
            html: p => esc(p.nombre) + (p.stock === 0 ? '<span class="insignia">Agotado</span>' : '')},
        {titulo: 'Stock', valor: p => p.stock, num: true},
        {titulo: 'Mínimo', valor: p => p.stockMinimo, num: true},
    ], r.stockBajo, {vacio: 'Todos los productos tienen stock suficiente.'});
    tabla($('#tabla-recientes', raiz), colMovimientos(), r.ultimosMovimientos, {vacio: 'Aún no hay movimientos.'});
    $('#ver-movimientos', raiz).onclick = () => irA('movimientos');
}

// ---------- Productos ----------

const filtroProductos = {texto: '', categoria: '', bajas: false};

async function dibujarProductos(raiz) {
    const productos = await api('GET', '/api/productos' + (filtroProductos.bajas ? '?bajas=1' : ''));
    const categorias = [...new Set(productos.map(p => p.categoria))].sort((a, b) => a.localeCompare(b, 'es'));
    const gestionar = puede('GESTIONAR_PRODUCTOS');
    const costos = puede('VER_REPORTES');
    raiz.innerHTML = `
        <div class="barra">
            <input type="search" id="buscar" placeholder="Buscar por código, nombre o categoría"
                   value="${esc(filtroProductos.texto)}" aria-label="Buscar productos">
            <select id="categoria" aria-label="Categoría">
                <option value="">Todas las categorías</option>
                ${categorias.map(c => `<option ${c === filtroProductos.categoria ? 'selected' : ''}>${esc(c)}</option>`).join('')}
            </select>
            ${gestionar ? `<label class="casilla"><input type="checkbox" id="ver-bajas" ${filtroProductos.bajas ? 'checked' : ''}>
                Ver dados de baja</label>` : ''}
            <span class="espacio"></span>
            ${costos ? '<a class="boton" href="/api/inventario.csv" download>Exportar CSV</a>' : ''}
            ${gestionar && !filtroProductos.bajas ? '<button class="boton primario" type="button" id="nuevo-producto">Nuevo producto</button>' : ''}
        </div>
        <div id="tabla-productos" class="tabla-contenedor"></div>
        <div id="pie-productos" class="pie-tabla"></div>`;

    const dibujarTabla = () => {
        const t = filtroProductos.texto.trim().toLowerCase();
        const visibles = productos.filter(p =>
            (!filtroProductos.categoria || p.categoria === filtroProductos.categoria)
            && (!t || [p.codigo, p.nombre, p.categoria].some(x => x.toLowerCase().includes(t))));
        const columnas = [
            {titulo: 'Código', valor: p => p.codigo},
            {titulo: 'Nombre', valor: p => p.nombre,
                html: p => esc(p.nombre) + (p.activo && p.stock === 0 ? '<span class="insignia">Agotado</span>' : '')},
            {titulo: 'Categoría', valor: p => p.categoria},
            {titulo: 'Precio', valor: p => Number(p.precio), num: true, html: p => numero(p.precio)},
            ...(costos ? [
                {titulo: 'Costo', valor: p => Number(p.costo), num: true, html: p => numero(p.costo)},
                {titulo: 'Margen', valor: p => Number(p.margen), num: true, html: p => porcentaje(p.margen)},
            ] : []),
            {titulo: 'Stock', valor: p => p.stock, num: true, html: p => entero(p.stock)},
            {titulo: 'Mínimo', valor: p => p.stockMinimo, num: true, html: p => entero(p.stockMinimo)},
            {titulo: 'Valor en stock', valor: p => Number(p.valorEnStock), num: true, html: p => numero(p.valorEnStock)},
            {titulo: '', clase: 'acciones', html: p => accionesProducto(p)},
        ];
        tabla($('#tabla-productos', raiz), columnas, visibles, {
            vacio: 'No hay productos que coincidan.',
            claseFila: p => !p.activo ? 'inactivo' : p.stockBajo ? 'bajo' : '',
        });
        const bajos = visibles.filter(p => p.stockBajo).length;
        $('#pie-productos', raiz).textContent = `${visibles.length} de ${productos.length} producto(s)`
            + (bajos ? `  ·  ${bajos} con stock bajo (resaltados)` : '');
    };

    const accionesProducto = p => {
        const c = esc(p.codigo);
        const b = (accion, texto, clase = '') =>
            `<button type="button" class="boton chico ${clase}" data-accion="${accion}" data-codigo="${c}">${texto}</button>`;
        if (!p.activo) {
            return b('historial', 'Historial') + (gestionar ? b('reactivar', 'Reactivar') : '');
        }
        return (puede('REGISTRAR_VENTAS') ? b('SALIDA', 'Venta') : '')
            + (puede('REGISTRAR_COMPRAS') ? b('ENTRADA', 'Compra') : '')
            + (puede('AJUSTAR_STOCK') ? b('AJUSTE', 'Ajustar') : '')
            + b('historial', 'Historial')
            + (gestionar ? b('editar', 'Editar') + b('baja', 'Baja', 'peligro') : '');
    };

    $('#tabla-productos', raiz).onclick = evento => {
        const boton = evento.target.closest('button[data-accion]');
        if (!boton) {
            return;
        }
        const p = productos.find(x => x.codigo === boton.dataset.codigo);
        const accion = boton.dataset.accion;
        if (['ENTRADA', 'SALIDA', 'AJUSTE'].includes(accion)) {
            movimientoProducto(p, accion);
        } else if (accion === 'editar') {
            formularioProducto(p, categorias);
        } else if (accion === 'historial') {
            historialProducto(p);
        } else if (accion === 'baja') {
            intentar(async () => {
                if (await confirmar('Dar de baja', `¿Dar de baja "${p.nombre}"? Saldrá del catálogo, pero su historial `
                    + 'se conserva y podrá reactivarlo.', 'Dar de baja')) {
                    await api('POST', `/api/productos/${encodeURIComponent(p.codigo)}/baja`);
                    aviso(`${p.nombre} se dio de baja.`);
                    refrescar();
                }
            });
        } else if (accion === 'reactivar') {
            intentar(async () => {
                await api('POST', `/api/productos/${encodeURIComponent(p.codigo)}/reactivar`);
                aviso(`${p.nombre} volvió al catálogo.`);
                refrescar();
            });
        }
    };

    $('#buscar', raiz).oninput = e => {
        filtroProductos.texto = e.target.value;
        dibujarTabla();
    };
    $('#categoria', raiz).onchange = e => {
        filtroProductos.categoria = e.target.value;
        dibujarTabla();
    };
    const verBajas = $('#ver-bajas', raiz);
    if (verBajas) {
        verBajas.onchange = e => {
            filtroProductos.bajas = e.target.checked;
            refrescar();
        };
    }
    const nuevo = $('#nuevo-producto', raiz);
    if (nuevo) {
        nuevo.onclick = () => formularioProducto(null, categorias);
    }
    dibujarTabla();
}

async function formularioProducto(p, categorias) {
    const nuevo = p === null;
    const listaCategorias = `<datalist id="lista-categorias">${categorias.map(c => `<option value="${esc(c)}">`).join('')}</datalist>`;
    const hecho = await dialogo({
        titulo: nuevo ? 'Nuevo producto' : `Editar ${p.codigo}`,
        textoAceptar: nuevo ? 'Registrar' : 'Guardar',
        cuerpo: `<div class="rejilla-campos">
            ${campo('Código', 'codigo', {valor: p?.codigo ?? '', extra: nuevo ? 'required placeholder="Ej. ARR-5K"' : 'readonly'})}
            ${campo('Categoría', 'categoria', {valor: p?.categoria ?? '', extra: 'list="lista-categorias" placeholder="General"'})}
            ${campo('Nombre', 'nombre', {valor: p?.nombre ?? '', completo: true, extra: 'required'})}
            ${campo('Precio de venta (S/)', 'precio', {valor: p?.precio ?? '', extra: 'required inputmode="decimal"'})}
            ${campo('Costo unitario (S/)', 'costo', {valor: p?.costo ?? '', extra: 'inputmode="decimal" placeholder="0.00"'})}
            ${nuevo ? campo('Stock inicial', 'stock', {valor: '0', extra: 'inputmode="numeric"'}) : ''}
            ${campo('Stock mínimo', 'stockMinimo', {valor: p?.stockMinimo ?? '0', extra: 'inputmode="numeric"'})}
        </div>${listaCategorias}`,
        alAceptar: v => nuevo
            ? api('POST', '/api/productos', v)
            : api('PUT', `/api/productos/${encodeURIComponent(p.codigo)}`, v),
    });
    if (hecho) {
        aviso(nuevo ? `Producto ${hecho.codigo} registrado.` : `Producto ${hecho.codigo} actualizado.`);
        refrescar();
    }
}

async function movimientoProducto(p, tipo) {
    const titulos = {ENTRADA: 'Registrar compra', SALIDA: 'Registrar venta', AJUSTE: 'Ajustar stock'};
    let proveedores = [];
    if (tipo === 'ENTRADA') {
        proveedores = await api('GET', '/api/proveedores').catch(() => []);
    }
    const notas = {ENTRADA: 'Ej. Factura 001', SALIDA: 'Ej. Boleta 123', AJUSTE: 'Motivo del ajuste'};
    const hecho = await dialogo({
        titulo: `${titulos[tipo]}: ${p.nombre}`,
        textoAceptar: 'Registrar',
        cuerpo: `
            <p class="suave">${esc(p.codigo)} · stock actual <strong>${entero(p.stock)}</strong> unidades</p>
            <div class="rejilla-campos">
                ${campo(tipo === 'AJUSTE' ? 'Stock contado' : 'Cantidad', 'cantidad',
                    {tipo: 'number', extra: 'required min="' + (tipo === 'AJUSTE' ? 0 : 1) + '" step="1"'})}
                ${tipo === 'ENTRADA' ? campo('Costo unitario (S/)', 'costo', {valor: p.costo ?? '', extra: 'inputmode="decimal"'}) : ''}
                ${tipo === 'SALIDA' ? `<label class="campo">Precio unitario<input value="${esc(moneda(p.precio))}" readonly></label>` : ''}
                ${tipo === 'ENTRADA' ? `<label class="campo completo">Proveedor
                    <select name="proveedorId"><option value="">(Sin proveedor)</option>
                    ${proveedores.map(x => `<option value="${x.id}">${esc(x.nombre)}</option>`).join('')}</select></label>` : ''}
                ${campo('Nota', 'nota', {completo: true, extra: `placeholder="${notas[tipo]}"`})}
            </div>
            <div class="resumen-operacion" id="resumen-operacion"></div>`,
        alAbrir: cuerpo => {
            // Muestra el total y el stock resultante mientras se escribe.
            const actualizar = () => {
                const cantidad = Number($('[name=cantidad]', cuerpo).value) || 0;
                const precio = tipo === 'SALIDA' ? Number(p.precio)
                    : tipo === 'ENTRADA' ? Number(String($('[name=costo]', cuerpo).value).replace(',', '.')) || 0 : 0;
                const resultante = tipo === 'SALIDA' ? p.stock - cantidad : tipo === 'ENTRADA' ? p.stock + cantidad : cantidad;
                $('#resumen-operacion', cuerpo).innerHTML =
                    (tipo !== 'AJUSTE' ? `<span>Total: <strong>${moneda(precio * cantidad)}</strong></span>` :
                        `<span>Diferencia: <strong>${cantidad - p.stock > 0 ? '+' : ''}${entero(cantidad - p.stock)}</strong></span>`)
                    + `<span>Stock después: <strong>${entero(resultante)}</strong></span>`;
            };
            cuerpo.oninput = actualizar;
            actualizar();
        },
        alAceptar: v => api('POST', `/api/productos/${encodeURIComponent(p.codigo)}/movimientos`, {
            tipo, cantidad: v.cantidad, costo: v.costo, nota: v.nota,
            proveedorId: v.proveedorId ? Number(v.proveedorId) : null,
        }),
    });
    if (hecho) {
        aviso(`${titulos[tipo].replace('Registrar ', '').replace('Ajustar stock', 'Ajuste')} registrado: ${hecho.nombre} tiene ${entero(hecho.stock)} unidades.`);
        if (tipo === 'SALIDA' && hecho.stockBajo) {
            aviso(`Atención: ${hecho.nombre} quedó con ${hecho.stock} unidades (mínimo ${hecho.stockMinimo}).`, true);
        }
        refrescar();
    }
}

async function historialProducto(p) {
    await intentar(async () => {
        const movimientos = await api('GET', `/api/productos/${encodeURIComponent(p.codigo)}/historial`);
        const hecho = dialogo({
            titulo: `Historial de ${p.codigo}`,
            soloCerrar: true,
            ancho: true,
            cuerpo: `<p class="suave">${esc(p.nombre)} · stock actual ${entero(p.stock)}</p>
                <div id="tabla-historial" class="tabla-contenedor"></div>`,
            alAceptar: () => true,
        });
        tabla($('#tabla-historial'), [...colMovimientos(false),
            {titulo: 'Usuario', valor: m => m.usuario},
            {titulo: 'Nota', valor: m => m.nota}], movimientos, {vacio: 'Sin movimientos registrados.'});
        await hecho;
    });
}

// ---------- Movimientos ----------

async function dibujarMovimientos(raiz) {
    const movimientos = await api('GET', '/api/movimientos');
    raiz.innerHTML = `
        <div class="barra">
            <input type="search" id="buscar-mov" placeholder="Buscar por producto, usuario o nota" aria-label="Buscar movimientos">
            <select id="tipo-mov" aria-label="Tipo">
                <option value="">Todos los tipos</option>
                <option value="SALIDA">Ventas</option><option value="ENTRADA">Compras</option><option value="AJUSTE">Ajustes</option>
            </select>
        </div>
        <div id="tabla-movimientos" class="tabla-contenedor"></div>
        <div id="pie-movimientos" class="pie-tabla"></div>`;
    const dibujarTabla = () => {
        const t = $('#buscar-mov', raiz).value.trim().toLowerCase();
        const tipo = $('#tipo-mov', raiz).value;
        const visibles = movimientos.filter(m => (!tipo || m.tipo === tipo)
            && (!t || [m.codigo, m.producto, m.usuario, m.nota].some(x => (x ?? '').toLowerCase().includes(t))));
        tabla($('#tabla-movimientos', raiz), [...colMovimientos(),
            {titulo: 'Usuario', valor: m => m.usuario},
            {titulo: 'Nota', valor: m => m.nota}], visibles.slice(0, 500), {vacio: 'No hay movimientos que coincidan.'});
        $('#pie-movimientos', raiz).textContent = `${visibles.length} movimiento(s)`
            + (visibles.length > 500 ? ' · se muestran los 500 más recientes' : '');
    };
    $('#buscar-mov', raiz).oninput = dibujarTabla;
    $('#tipo-mov', raiz).onchange = dibujarTabla;
    dibujarTabla();
}

// ---------- Proveedores ----------

async function dibujarProveedores(raiz) {
    const proveedores = await api('GET', '/api/proveedores?todos=1');
    raiz.innerHTML = `
        <div class="barra"><span class="espacio"></span>
            <button class="boton primario" type="button" id="nuevo-proveedor">Nuevo proveedor</button></div>
        <div id="tabla-proveedores" class="tabla-contenedor"></div>`;
    tabla($('#tabla-proveedores', raiz), [
        {titulo: 'Nombre', valor: x => x.nombre},
        {titulo: 'RUC / DNI', valor: x => x.documento},
        {titulo: 'Teléfono', valor: x => x.telefono},
        {titulo: 'Correo', valor: x => x.email},
        {titulo: 'Estado', valor: x => x.activo ? 'Activo' : 'De baja'},
        {titulo: '', clase: 'acciones', html: x =>
            `<button type="button" class="boton chico" data-accion="editar" data-id="${x.id}">Editar</button>`
            + (x.activo
                ? `<button type="button" class="boton chico peligro" data-accion="baja" data-id="${x.id}">Baja</button>`
                : `<button type="button" class="boton chico" data-accion="reactivar" data-id="${x.id}">Reactivar</button>`)},
    ], proveedores, {vacio: 'Aún no hay proveedores.', claseFila: x => x.activo ? '' : 'inactivo'});

    $('#nuevo-proveedor', raiz).onclick = () => formularioProveedor(null);
    $('#tabla-proveedores', raiz).onclick = e => {
        const b = e.target.closest('button[data-accion]');
        if (!b) {
            return;
        }
        const x = proveedores.find(p => p.id === Number(b.dataset.id));
        if (b.dataset.accion === 'editar') {
            formularioProveedor(x);
        } else {
            intentar(async () => {
                await api('POST', `/api/proveedores/${x.id}/${b.dataset.accion}`);
                refrescar();
            });
        }
    };
}

async function formularioProveedor(x) {
    const hecho = await dialogo({
        titulo: x ? 'Editar proveedor' : 'Nuevo proveedor',
        cuerpo: `<div class="rejilla-campos">
            ${campo('Nombre', 'nombre', {valor: x?.nombre ?? '', completo: true, extra: 'required'})}
            ${campo('RUC o DNI', 'documento', {valor: x?.documento ?? '', ayuda: 'RUC de 11 dígitos o DNI de 8', extra: 'inputmode="numeric"'})}
            ${campo('Teléfono', 'telefono', {valor: x?.telefono ?? '', extra: 'inputmode="tel"'})}
            ${campo('Correo', 'email', {tipo: 'email', valor: x?.email ?? '', completo: true})}
        </div>`,
        alAceptar: v => x ? api('PUT', `/api/proveedores/${x.id}`, v) : api('POST', '/api/proveedores', v),
    });
    if (hecho) {
        aviso(`Proveedor ${hecho.nombre} guardado.`);
        refrescar();
    }
}

// ---------- Reportes ----------

const periodo = {desde: hoyIso(-29), hasta: hoyIso(), diasSinVenta: 30};

async function dibujarReportes(raiz) {
    const consulta = new URLSearchParams(periodo).toString();
    const r = await api('GET', '/api/reportes?' + consulta);
    const tarjeta = (etiqueta, valor) =>
        `<div class="tarjeta"><div class="etiqueta">${esc(etiqueta)}</div><div class="valor">${esc(valor)}</div></div>`;
    raiz.innerHTML = `
        <div class="barra">
            <label class="casilla">Desde <input type="date" id="desde" value="${periodo.desde}"></label>
            <label class="casilla">Hasta <input type="date" id="hasta" value="${periodo.hasta}"></label>
            <button class="boton" type="button" data-dias="7">7 días</button>
            <button class="boton" type="button" data-dias="30">30 días</button>
            <button class="boton" type="button" data-dias="90">90 días</button>
            <span class="espacio"></span>
            <a class="boton" href="/api/reportes/ventas.csv?${esc(consulta)}" download>Exportar ventas CSV</a>
        </div>
        <section class="tarjetas">
            ${tarjeta('Ingresos', moneda(r.ingresos))}
            ${tarjeta('Costo de lo vendido', moneda(r.costo))}
            ${tarjeta('Ganancia', moneda(r.ganancia))}
            ${tarjeta('Margen', porcentaje(r.margen))}
            ${tarjeta('Unidades vendidas', entero(r.unidades))}
        </section>
        <section class="panel"><h3>Ventas por día</h3><div id="grafico"></div></section>
        <section class="paneles">
            <div class="panel"><h3>Más vendidos (unidades)</h3><div id="top" class="barras-horizontales"></div></div>
            <div class="panel"><h3>Ventas por producto</h3><div id="tabla-ventas" class="tabla-contenedor"></div></div>
        </section>
        <section class="paneles">
            <div class="panel"><h3>Compras por proveedor</h3><div id="tabla-compras" class="tabla-contenedor"></div></div>
            <div class="panel">
                <div class="panel-cabecera"><h3>Sin ventas en los últimos</h3>
                    <label class="casilla"><input type="number" id="dias-sin-venta" min="1" max="365" value="${periodo.diasSinVenta}"> días</label>
                </div>
                <div id="tabla-sin-rotacion" class="tabla-contenedor"></div>
            </div>
        </section>`;

    graficoDiario($('#grafico', raiz), r.porDia);
    const maximo = Math.max(1, ...r.masVendidos.map(l => l.unidades));
    $('#top', raiz).innerHTML = r.masVendidos.length === 0 ? '<p class="suave">Sin ventas en el período.</p>'
        : r.masVendidos.map(l => `<div class="barra-h"><span>${esc(l.nombre)}</span><strong>${entero(l.unidades)}</strong>
            <div class="pista"><div class="relleno" data-ancho="${(100 * l.unidades / maximo).toFixed(1)}"></div></div></div>`).join('');
    $$('.relleno', raiz).forEach(el => el.style.width = el.dataset.ancho + '%');

    tabla($('#tabla-ventas', raiz), [
        {titulo: 'Producto', valor: l => l.nombre},
        {titulo: 'Unid.', valor: l => l.unidades, num: true},
        {titulo: 'Ingresos', valor: l => Number(l.ingresos), num: true, html: l => numero(l.ingresos)},
        {titulo: 'Ganancia', valor: l => Number(l.ganancia), num: true, html: l => numero(l.ganancia)},
        {titulo: 'Margen', valor: l => Number(l.margen), num: true, html: l => porcentaje(l.margen)},
    ], r.lineas, {vacio: 'Sin ventas en el período.'});
    tabla($('#tabla-compras', raiz), [
        {titulo: 'Proveedor', valor: c => c.proveedor},
        {titulo: 'Compras', valor: c => c.compras, num: true},
        {titulo: 'Unidades', valor: c => c.unidades, num: true},
        {titulo: 'Monto', valor: c => Number(c.monto), num: true, html: c => numero(c.monto)},
    ], r.compras, {vacio: 'Sin compras en el período.'});
    tabla($('#tabla-sin-rotacion', raiz), [
        {titulo: 'Producto', valor: q => q.nombre},
        {titulo: 'Stock', valor: q => q.stock, num: true},
        {titulo: 'Al costo', valor: q => Number(q.costoEnStock), num: true, html: q => numero(q.costoEnStock)},
        {titulo: 'Última venta', valor: q => q.ultimaVenta ?? '', html: q => q.ultimaVenta
                ? `${fechaCorta(q.ultimaVenta)} <span class="suave">(hace ${q.diasSinVenta} d)</span>` : 'Nunca'},
    ], r.sinRotacion, {vacio: 'Todos los productos con stock se vendieron en ese plazo.'});

    const cambiar = () => {
        periodo.desde = $('#desde', raiz).value;
        periodo.hasta = $('#hasta', raiz).value;
        periodo.diasSinVenta = $('#dias-sin-venta', raiz).value;
        refrescar();
    };
    $('#desde', raiz).onchange = cambiar;
    $('#hasta', raiz).onchange = cambiar;
    $('#dias-sin-venta', raiz).onchange = cambiar;
    $$('[data-dias]', raiz).forEach(b => b.onclick = () => {
        periodo.desde = hoyIso(1 - Number(b.dataset.dias));
        periodo.hasta = hoyIso();
        refrescar();
    });
}

/** Gráfico de barras en SVG (sin librerías): ingresos de cada día del período. */
function graficoDiario(contenedor, dias) {
    const ancho = 1000, alto = 220, margenIzq = 56, margenInf = 24, margenSup = 10;
    const maximo = Math.max(1, ...dias.map(d => Number(d.ingresos)));
    const paso = (ancho - margenIzq) / Math.max(1, dias.length);
    const barra = Math.max(1, paso * 0.7);
    const y = v => alto - margenInf - (alto - margenInf - margenSup) * v / maximo;
    const cadaCuanto = Math.ceil(dias.length / 10);
    let svg = `<svg class="grafico" viewBox="0 0 ${ancho} ${alto}" preserveAspectRatio="none" role="img"
        aria-label="Ingresos por día">`;
    for (const f of [0, 0.5, 1]) {
        const v = maximo * f;
        svg += `<line class="eje" x1="${margenIzq}" x2="${ancho}" y1="${y(v)}" y2="${y(v)}"/>
            <text x="${margenIzq - 6}" y="${y(v) + 4}" text-anchor="end">${esc(formatoEntero.format(Math.round(v)))}</text>`;
    }
    dias.forEach((d, i) => {
        const x = margenIzq + i * paso + (paso - barra) / 2;
        const v = Number(d.ingresos);
        svg += `<rect class="barra-ingreso" x="${x}" y="${y(v)}" width="${barra}" height="${Math.max(0, y(0) - y(v))}">
            <title>${esc(fechaCorta(d.fecha))}: ${esc(moneda(v))} (ganancia ${esc(moneda(d.ganancia))})</title></rect>`;
        if (i % cadaCuanto === 0) {
            svg += `<text x="${x + barra / 2}" y="${alto - 6}" text-anchor="middle">${esc(fechaCorta(d.fecha).slice(0, 5))}</text>`;
        }
    });
    contenedor.innerHTML = svg + '</svg>';
}

// ---------- Usuarios ----------

async function dibujarUsuarios(raiz) {
    const usuarios = await api('GET', '/api/usuarios');
    raiz.innerHTML = `
        <div class="barra"><span class="suave">Administrador: acceso completo · Vendedor: consulta productos y registra ventas.</span>
            <span class="espacio"></span>
            <button class="boton primario" type="button" id="nuevo-usuario">Nuevo usuario</button></div>
        <div id="tabla-usuarios" class="tabla-contenedor"></div>`;
    tabla($('#tabla-usuarios', raiz), [
        {titulo: 'Usuario', valor: u => u.usuario},
        {titulo: 'Nombre', valor: u => u.nombre},
        {titulo: 'Rol', valor: u => u.rolNombre},
        {titulo: 'Estado', valor: u => u.activo ? 'Activo' : 'Desactivado'},
        {titulo: '', clase: 'acciones', html: u => {
            const b = (accion, texto, clase = '') =>
                `<button type="button" class="boton chico ${clase}" data-accion="${accion}" data-usuario="${esc(u.usuario)}">${texto}</button>`;
            return b('rol', 'Cambiar rol') + b('contrasena', 'Restablecer contraseña')
                + (u.activo ? b('desactivar', 'Desactivar', 'peligro') : b('activar', 'Activar'));
        }},
    ], usuarios, {claseFila: u => u.activo ? '' : 'inactivo'});

    $('#nuevo-usuario', raiz).onclick = async () => {
        const hecho = await dialogo({
            titulo: 'Nuevo usuario',
            textoAceptar: 'Crear',
            cuerpo: `<div class="rejilla-campos">
                ${campo('Usuario', 'usuario', {extra: 'required placeholder="Ej. lquispe" autocapitalize="none"'})}
                <label class="campo">Rol<select name="rol"><option value="VENDEDOR">Vendedor</option>
                    <option value="ADMINISTRADOR">Administrador</option></select></label>
                ${campo('Nombre completo', 'nombre', {completo: true, extra: 'required'})}
                ${campo('Contraseña inicial', 'contrasena', {tipo: 'password', completo: true,
                    ayuda: 'Mínimo 8 caracteres, letras y números.', extra: 'required autocomplete="new-password"'})}
            </div>`,
            alAceptar: v => api('POST', '/api/usuarios', v),
        });
        if (hecho) {
            aviso(`Usuario ${hecho.usuario} creado.`);
            refrescar();
        }
    };

    $('#tabla-usuarios', raiz).onclick = e => {
        const b = e.target.closest('button[data-accion]');
        if (!b) {
            return;
        }
        const u = usuarios.find(x => x.usuario === b.dataset.usuario);
        const ruta = `/api/usuarios/${encodeURIComponent(u.usuario)}`;
        intentar(async () => {
            if (b.dataset.accion === 'rol') {
                const hecho = await dialogo({
                    titulo: `Rol de ${u.nombre}`,
                    cuerpo: `<label class="campo">Rol<select name="rol">
                        <option value="VENDEDOR" ${u.rol === 'VENDEDOR' ? 'selected' : ''}>Vendedor</option>
                        <option value="ADMINISTRADOR" ${u.rol === 'ADMINISTRADOR' ? 'selected' : ''}>Administrador</option>
                        </select></label>`,
                    alAceptar: v => api('PUT', ruta + '/rol', v),
                });
                if (hecho && u.usuario === estado.usuario.usuario) {
                    location.reload(); // Cambió su propio rol: se recargan los permisos.
                    return;
                }
            } else if (b.dataset.accion === 'contrasena') {
                const hecho = await dialogo({
                    titulo: `Restablecer contraseña de ${u.nombre}`,
                    cuerpo: campo('Nueva contraseña', 'contrasena', {tipo: 'password', ayuda: 'Mínimo 8 caracteres, letras y números.',
                        extra: 'required autocomplete="new-password"'}),
                    alAceptar: v => api('POST', ruta + '/contrasena', v),
                });
                if (hecho) {
                    aviso(`Comunique la nueva contraseña a ${u.nombre}.`);
                }
            } else if (b.dataset.accion === 'desactivar') {
                if (await confirmar('Desactivar usuario', `¿Desactivar a ${u.nombre}? No podrá iniciar sesión; `
                    + 'sus movimientos se conservan.', 'Desactivar')) {
                    await api('POST', ruta + '/desactivar');
                }
            } else {
                await api('POST', ruta + '/activar');
            }
            refrescar();
        });
    };
}

iniciar();

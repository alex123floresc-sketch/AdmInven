'use strict';
/*
 * Utilidades que comparten la página del personal (app.js) y la tienda para clientes (tienda.js):
 * formatos, íconos, llamadas a la API, avisos y el tema claro u oscuro.
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

/** Íconos de trazo (24×24) para pestañas y botones; toman el color del texto. */
const ICONOS = {
    resumen: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/>'
        + '<rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
    productos: '<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="m3 8 9 5 9-5M12 13v8"/>',
    caja: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/>'
        + '<path d="M2 3h3l2.7 12.2a1.5 1.5 0 0 0 1.5 1.2h8.6a1.5 1.5 0 0 0 1.5-1.2L21 7H6"/>',
    movimientos: '<path d="M7 4v16m0 0-3-3m3 3 3-3M17 20V4m0 0-3 3m3-3 3 3"/>',
    proveedores: '<path d="M3 6h11v10H3zM14 10h4l3 3v3h-7"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>',
    reportes: '<path d="M4 20V10m6 10V4m6 16v-7m4 7H2"/>',
    usuarios: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2'
        + 'M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    luna: '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>',
    pedidos: '<path d="M6 7h12l-1 13H7z"/><path d="M9 10V6a3 3 0 0 1 6 0v4"/>',
    buscar: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    tienda: '<path d="M4 9h16l-1.5-5h-13zM5 9v11h14V9M9 20v-6h6v6"/>',
};

const icono = nombre => `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONOS[nombre]}</svg>`;

// ---------- Comunicación con el servidor ----------

class ErrorApi extends Error {
    constructor(mensaje, estado) {
        super(mensaje);
        this.estado = estado;
    }
}

/** Llama a la API; si la respuesta es un error, lanza {@link ErrorApi} con el mensaje del servidor. */
async function llamarApi(metodo, url, cuerpo) {
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
    if (respuesta.status === 204) {
        return null;
    }
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
        throw new ErrorApi(datos?.error ?? `Error ${respuesta.status}`, respuesta.status);
    }
    return datos;
}

function aviso(mensaje, error = false) {
    const el = document.createElement('div');
    el.className = 'aviso' + (error ? ' error' : '');
    el.textContent = mensaje;
    $('#avisos').append(el);
    setTimeout(() => el.remove(), error ? 6000 : 3500);
}

// ---------- Tema claro u oscuro ----------

/** localStorage puede no estar disponible (navegación privada): entonces el tema simplemente no se recuerda. */
function leerPreferencia(clave) {
    try {
        return localStorage.getItem(clave);
    } catch {
        return null;
    }
}

function guardarPreferencia(clave, valor) {
    try {
        localStorage.setItem(clave, valor);
    } catch {
        // Sin almacenamiento: la preferencia dura hasta cerrar la página.
    }
}

const temaDelSistema = matchMedia('(prefers-color-scheme: dark)');
const temaActual = () => document.documentElement.dataset.tema ?? (temaDelSistema.matches ? 'oscuro' : 'claro');

function aplicarTema(tema) {
    if (tema === 'claro' || tema === 'oscuro') {
        document.documentElement.dataset.tema = tema;
    }
    const oscuro = temaActual() === 'oscuro';
    $$('.boton-tema').forEach(b => {
        b.innerHTML = icono(oscuro ? 'sol' : 'luna');
        b.title = oscuro ? 'Usar tema claro' : 'Usar tema oscuro';
        b.setAttribute('aria-label', b.title);
    });
}

function iniciarTema() {
    aplicarTema(leerPreferencia('tema'));
    temaDelSistema.onchange = () => aplicarTema();
    $$('.boton-tema').forEach(b => b.onclick = () => {
        const nuevo = temaActual() === 'oscuro' ? 'claro' : 'oscuro';
        guardarPreferencia('tema', nuevo);
        aplicarTema(nuevo);
    });
}

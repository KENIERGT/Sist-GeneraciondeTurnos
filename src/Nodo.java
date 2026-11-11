/** Nodo de Cola y ListaEnlazada. */
class Nodo<T> {
    T dato;
    Nodo<T> siguiente;

    Nodo(T dato) {
        this.dato = dato;
        this.siguiente = null;
    }
}

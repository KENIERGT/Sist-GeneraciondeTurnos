import java.util.Iterator;
import java.util.NoSuchElementException;

/** Lista enlazada simple. Guarda referencia al último para agregar en O(1). */
public class ListaEnlazada<T> implements Iterable<T> {
    private Nodo<T> cabeza;
    private Nodo<T> cola;
    private int tamano;

    public void agregar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (cabeza == null) {
            cabeza = nuevo;
            cola = nuevo;
        } else {
            cola.siguiente = nuevo;
            cola = nuevo;
        }
        tamano++;
    }

    /** Quita el primer nodo con ese dato; false si no estaba. */
    public boolean eliminar(T dato) {
        Nodo<T> anterior = null;
        for (Nodo<T> actual = cabeza; actual != null; anterior = actual, actual = actual.siguiente) {
            if (actual.dato == dato) {
                if (anterior == null) {
                    cabeza = actual.siguiente;
                } else {
                    anterior.siguiente = actual.siguiente;
                }
                if (actual == cola) {
                    cola = anterior;
                }
                tamano--;
                return true;
            }
        }
        return false;
    }

    public int tamano() {
        return tamano;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Nodo<T> actual = cabeza;

            @Override
            public boolean hasNext() {
                return actual != null;
            }

            @Override
            public T next() {
                if (actual == null) {
                    throw new NoSuchElementException();
                }
                T dato = actual.dato;
                actual = actual.siguiente;
                return dato;
            }
        };
    }
}

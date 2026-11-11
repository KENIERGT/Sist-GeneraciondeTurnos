import java.util.Iterator;
import java.util.NoSuchElementException;

/** Cola FIFO: el primero que entra es el primero que sale. */
public class Cola<T> implements Iterable<T> {
    private Nodo<T> frente;
    private Nodo<T> fin;
    private int tamano;

    public void encolar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (fin == null) {
            frente = nuevo;
        } else {
            fin.siguiente = nuevo;
        }
        fin = nuevo;
        tamano++;
    }

    /** Saca el del frente; null si está vacía. */
    public T desencolar() {
        if (frente == null) {
            return null;
        }
        T dato = frente.dato;
        frente = frente.siguiente;
        if (frente == null) {
            fin = null;
        }
        tamano--;
        return dato;
    }

    /** Quita un dato de cualquier posición, sin alterar el orden de los demás; false si no estaba. */
    public boolean eliminar(T dato) {
        Nodo<T> anterior = null;
        for (Nodo<T> actual = frente; actual != null; anterior = actual, actual = actual.siguiente) {
            if (actual.dato == dato) {
                if (anterior == null) {
                    frente = actual.siguiente;
                } else {
                    anterior.siguiente = actual.siguiente;
                }
                if (actual == fin) {
                    fin = anterior;
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

    public boolean estaVacia() {
        return tamano == 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Nodo<T> actual = frente;

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

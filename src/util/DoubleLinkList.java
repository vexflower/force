package util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

public class DoubleLinkList<E, V> implements Iterable<E>
{
    int size;
    transient volatile Node<E, V> first;
    transient volatile Node<E, V> last;

    public DoubleLinkList()
    {
        size = 0;
    }

    public boolean add(E e, V v, int index)
    {
        if(index == size) {
            return addLast(e, v);
        }
        else {
            return addBefore(e, v, node(index));
        }
    }

    public boolean addFirst(E e, V v)
    {
        final Node<E, V> f = first;
        final Node<E, V> newNode = new Node<>(e, v, f, null);
        first = newNode;
        if(f == null) {
            last = newNode;
        }
        else {
            f.prev = newNode;
        }
        size++;
        return true;
    }

    public boolean addLast(E e, V v)
    {
        final Node<E, V> l = last;
        final Node<E, V> newNode = new Node<>(e, v, null, l);
        last = newNode;
        if(l == null) {
            first = newNode;
        }
        else {
            l.next = newNode;
        }
        size++;
        return true;
    }

    private boolean addBefore(E e, V v, Node<E, V> node)
    {
        assert node != null;
        final Node<E, V> pred = node.prev;
        final Node<E, V> newNode = new Node<>(e, v, node, pred);
        node.prev = newNode;
        if(pred == null) {
            first = newNode;
        }
        else {
            pred.next = newNode;
        }
        size++;
        return true;
    }

    public E removeFirst()
    {
        return unlink(first);
    }

    public E removeLast()
    {
        return unlink(last);
    }

    public E remove(int index)
    {
        checkElementIndex(index);
        return unlink(node(index));
    }

    public boolean remove(E element)
    {
        if(element == null) {
            for(Node<E, V> x = first; x != null; x = x.next) {
                if(x.e == null) {
                    unlink(x);
                    return true;
                }
            }
        }
        else {
            for(Node<E, V> x = first; x != null; x = x.next) {
                if(element.equals(x.e)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false; // Element not found
    }

    public void clear()
    {
        for(Node<E, V> x = first; x != null; ) {
            Node<E, V> next = x.next;
            x.e = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
    }

    private Node<E, V> node(int index)
    {
        assert index <= size && size >= 0;
        Node<E, V> n;
        if(index < (size >> 1)) {
            n = first;
            for(int i = 0; i < index; i++) {
                n = n.next;
            }
        }
        else {
            n = last;
            for(int i = size - 1; i > index; i--) {
                n = n.prev;
            }
        }
        return n;
    }

    private E unlink(Node<E, V> e)
    {
        assert e != null;
        final E element = e.e;
        final Node<E, V> next = e.next;
        final Node<E, V> prev = e.prev;

        if(prev == null) {
            first = next;
        }
        else {
            prev.next = next;
            e.prev = null;
        }

        if(next == null) {
            last = prev;
        }
        else {
            next.prev = prev;
            e.next = null;
        }

        e.e = null;
        size--;
        return element;
    }

    @Override
    public Iterator<E> iterator()
    {
        return new Iterator<>()
        {
            int i = 0;
            Node<E, V> e;

            @Override
            public boolean hasNext()
            {
                if(first == null)
                    return false;
                if(i == 0)
                    e = first;
                if(i < size)
                    return true;
                else {
                    i = 0;
                    e = null;
                    return false;
                }
            }

            @Override
            public E next()
            {
                if(!hasNext()) {
                    throw new NoSuchElementException();
                }
                E element = e.e;
                e = e.next;
                i++;
                return element;
            }
        };
    }

    @Override
    public void forEach(Consumer<? super E> action)
    {
        Iterable.super.forEach(action);
    }

    @Override
    public Spliterator<E> spliterator()
    {
        return Iterable.super.spliterator();
    }

    public int size()
    {
        return size;
    }

    public int length()
    {
        int len = size;
        return len;
    }

    public E getFirst()
    {
        return first.e;
    }

    public E getLast()
    {
        return last.e;
    }

    public E get(int i)
    {
        checkElementIndex(i);
        return node(i).e;
    }

    private void checkElementIndex(int i)
    {
        if (!(i >= 0 && i < size))
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + size);
    }

    public boolean isEmpty()
    {
        return size == 0;
    }

    private static class Node<E, T>
    {
        E e;
        T t;
        Node<E, T> prev;
        Node<E, T> next;

        public Node(E e, T t, Node<E, T> next, Node<E, T> prev)
        {
            this.e = e;
            this.t = t;
            this.next = next;
            this.prev = prev;
        }
    }
}

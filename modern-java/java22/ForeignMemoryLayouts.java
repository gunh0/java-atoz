// JEP 454 — Foreign Function & Memory API (Java 22): memory layouts
//
// Native data is described by C structs, not Java classes. Computing field
// offsets, padding and array strides by hand (as with ByteBuffer.getInt(8))
// is exactly the kind of arithmetic that silently goes wrong. A
// MemoryLayout describes the shape once: value layouts for scalars,
// structLayout for structs (padding is explicit), sequenceLayout for
// arrays. From a layout you derive sizes, offsets and VarHandles that
// access a field by name, e.g. points[i].y, with bounds and alignment
// checked by the runtime.
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class ForeignMemoryLayouts {
    // struct reading { char sensor; int value; double weight; }
    // C inserts 3 bytes of padding after `sensor` so `value` is 4-aligned.
    static final StructLayout READING = MemoryLayout.structLayout(
            ValueLayout.JAVA_BYTE.withName("sensor"),
            MemoryLayout.paddingLayout(3),
            ValueLayout.JAVA_INT.withName("value"),
            ValueLayout.JAVA_DOUBLE.withName("weight"));

    // struct reading readings[4];
    static final SequenceLayout READINGS = MemoryLayout.sequenceLayout(4, READING);

    // Access readings[i].value: the free index i becomes a coordinate of the handle
    static final VarHandle VALUE = READINGS.varHandle(
            PathElement.sequenceElement(), PathElement.groupElement("value"));
    static final VarHandle SENSOR = READINGS.varHandle(
            PathElement.sequenceElement(), PathElement.groupElement("sensor"));
    static final VarHandle WEIGHT = READINGS.varHandle(
            PathElement.sequenceElement(), PathElement.groupElement("weight"));

    public static void main(String[] args) {
        System.out.println("sizeof(struct reading) = " + READING.byteSize()
                + ", alignment = " + READING.byteAlignment());
        for (String field : new String[]{"sensor", "value", "weight"}) {
            System.out.println("  offsetof(" + field + ") = " + READING.byteOffset(PathElement.groupElement(field)));
        }
        System.out.println("sizeof(readings) = " + READINGS.byteSize());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment readings = arena.allocate(READINGS);
            for (long i = 0; i < READINGS.elementCount(); i++) {
                // VarHandle coordinates: segment, base offset, then the sequence index
                SENSOR.set(readings, 0L, i, (byte) ('A' + i));
                VALUE.set(readings, 0L, i, (int) (100 * (i + 1)));
                WEIGHT.set(readings, 0L, i, 0.5 * (i + 1));
            }

            double weighted = 0;
            for (long i = 0; i < READINGS.elementCount(); i++) {
                char sensor = (char) (byte) SENSOR.get(readings, 0L, i);
                int value = (int) VALUE.get(readings, 0L, i);
                double weight = (double) WEIGHT.get(readings, 0L, i);
                System.out.println("  readings[" + i + "] sensor=" + sensor + " value=" + value + " weight=" + weight);
                weighted += value * weight;
            }
            System.out.println("weighted sum = " + weighted);

            // The same bytes, seen without the layout: hand-computed offsets
            long offsetOfThirdValue = 2 * READING.byteSize() + 4;
            System.out.println("raw int at offset " + offsetOfThirdValue + " = "
                    + readings.get(ValueLayout.JAVA_INT, offsetOfThirdValue));

            // Index 4 is outside the sequence: rejected, not silently read
            try {
                VALUE.get(readings, 0L, 4L);
            } catch (IndexOutOfBoundsException e) {
                System.out.println("readings[4].value: " + e.getClass().getSimpleName());
            }

            // A misaligned layout is caught when the layout is built
            try {
                MemoryLayout.structLayout(ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT);
            } catch (IllegalArgumentException e) {
                System.out.println("struct without padding: " + e.getClass().getSimpleName());
            }
        }
    }
}

/* Output:
sizeof(struct reading) = 16, alignment = 8
  offsetof(sensor) = 0
  offsetof(value) = 4
  offsetof(weight) = 8
sizeof(readings) = 64
  readings[0] sensor=A value=100 weight=0.5
  readings[1] sensor=B value=200 weight=1.0
  readings[2] sensor=C value=300 weight=1.5
  readings[3] sensor=D value=400 weight=2.0
weighted sum = 1500.0
raw int at offset 36 = 300
readings[4].value: IndexOutOfBoundsException
struct without padding: IllegalArgumentException
*/

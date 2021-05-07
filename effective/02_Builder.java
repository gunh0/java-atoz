// Effective Java, Item 2: Consider a builder when faced with many constructor parameters.
//
// When a class has a few required and many optional parameters, both classic
// answers hurt. Telescoping constructors force callers to pass positional
// values whose meaning is invisible at the call site (swap two ints and it
// still compiles). The JavaBeans style (no-arg constructor + setters) reads
// well but leaves the object mutable and observable in a half-built state.
// A Builder gives named, optional arguments, validates them once in build(),
// and returns an immutable object.
public class BuilderDemo {
    public static void main(String[] args) {
        // Telescoping: what do 240, 8, 100, 0, 35 mean? Which one is sodium?
        TelescopingNutrition cola = new TelescopingNutrition(240, 8, 100, 0, 35);
        System.out.println("telescoping:    " + cola);

        // JavaBeans: readable, but nothing prevents using a half-built object
        BeanNutrition bean = new BeanNutrition();
        bean.setServingSize(240);
        bean.setServings(8);
        System.out.println("half-built bean: " + bean);
        bean.setCalories(100);
        bean.setCarbohydrate(35);
        System.out.println("finished bean:   " + bean);

        // Builder: required values in the Builder constructor, optional ones by name
        NutritionFacts facts = new NutritionFacts.Builder(240, 8)
                .calories(100)
                .carbohydrate(35)
                .build();
        System.out.println("builder:        " + facts);

        // Invariants are checked in one place, before the object exists
        try {
            new NutritionFacts.Builder(240, 0).calories(100).build();
        } catch (IllegalStateException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}

// Anti-pattern 1: telescoping constructors
class TelescopingNutrition {
    private final int servingSize, servings, calories, fat, sodium, carbohydrate;

    TelescopingNutrition(int servingSize, int servings) {
        this(servingSize, servings, 0);
    }

    TelescopingNutrition(int servingSize, int servings, int calories) {
        this(servingSize, servings, calories, 0);
    }

    TelescopingNutrition(int servingSize, int servings, int calories, int fat) {
        this(servingSize, servings, calories, fat, 0);
    }

    TelescopingNutrition(int servingSize, int servings, int calories, int fat, int sodium) {
        this(servingSize, servings, calories, fat, sodium, 0);
    }

    TelescopingNutrition(int servingSize, int servings, int calories, int fat, int sodium, int carbohydrate) {
        this.servingSize = servingSize;
        this.servings = servings;
        this.calories = calories;
        this.fat = fat;
        this.sodium = sodium;
        this.carbohydrate = carbohydrate;
    }

    @Override
    public String toString() {
        // The caller meant carbohydrate = 35, but it landed in sodium
        return "calories=" + calories + ", sodium=" + sodium + ", carbohydrate=" + carbohydrate;
    }
}

// Anti-pattern 2: JavaBeans (mutable, may be inconsistent mid-construction)
class BeanNutrition {
    private int servingSize = -1; // required, but nothing enforces it
    private int servings = -1;
    private int calories, carbohydrate;

    void setServingSize(int v) { servingSize = v; }
    void setServings(int v) { servings = v; }
    void setCalories(int v) { calories = v; }
    void setCarbohydrate(int v) { carbohydrate = v; }

    @Override
    public String toString() {
        return "servings=" + servings + ", calories=" + calories + ", carbohydrate=" + carbohydrate;
    }
}

// Recommended: Builder
final class NutritionFacts {
    private final int servingSize, servings, calories, fat, sodium, carbohydrate;

    static final class Builder {
        // Required parameters
        private final int servingSize;
        private final int servings;
        // Optional parameters, initialized to defaults
        private int calories, fat, sodium, carbohydrate;

        Builder(int servingSize, int servings) {
            this.servingSize = servingSize;
            this.servings = servings;
        }

        Builder calories(int v) { calories = v; return this; }
        Builder fat(int v) { fat = v; return this; }
        Builder sodium(int v) { sodium = v; return this; }
        Builder carbohydrate(int v) { carbohydrate = v; return this; }

        NutritionFacts build() {
            if (servings <= 0) {
                throw new IllegalStateException("servings must be positive: " + servings);
            }
            return new NutritionFacts(this);
        }
    }

    private NutritionFacts(Builder b) {
        servingSize = b.servingSize;
        servings = b.servings;
        calories = b.calories;
        fat = b.fat;
        sodium = b.sodium;
        carbohydrate = b.carbohydrate;
    }

    @Override
    public String toString() {
        return "calories=" + calories + ", sodium=" + sodium + ", carbohydrate=" + carbohydrate;
    }
}

/* Output:
telescoping:    calories=100, sodium=35, carbohydrate=0
half-built bean: servings=8, calories=0, carbohydrate=0
finished bean:   servings=8, calories=100, carbohydrate=35
builder:        calories=100, sodium=0, carbohydrate=35
rejected: servings must be positive: 0
*/

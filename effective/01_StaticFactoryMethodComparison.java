public class Main {
    public static Boolean valueOf(boolean b) {
        return b ? Boolean.TRUE : Boolean.FALSE;
    }

    public static void main(String[] args) {
        boolean testBoolean = true;

        // Creating objects using valueOf method
        long valueOfStartTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            Boolean result = valueOf(testBoolean);
        }
        long valueOfEndTime = System.currentTimeMillis();
        long valueOfTotalTime = valueOfEndTime - valueOfStartTime;

        // Creating objects directly
        long directCreationStartTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            Boolean result = new Boolean(testBoolean);
        }
        long directCreationEndTime = System.currentTimeMillis();
        long directCreationTotalTime = directCreationEndTime - directCreationStartTime;

        System.out.println("Total time taken using valueOf: " + valueOfTotalTime + " milliseconds");
        System.out.println("Total time taken using direct creation: " + directCreationTotalTime + " milliseconds");
    }
}

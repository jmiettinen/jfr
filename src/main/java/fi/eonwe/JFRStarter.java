package fi.eonwe;

public class JFRStarter {

    public static void main(final String[] args) throws Exception {
        JFR.recordFor(3); // Record to CWD
        long startTime = System.currentTimeMillis();

        int sum = 0;
        while (System.currentTimeMillis() < startTime + 5 * 1000) {
            System.out.println(JFR.checkRecordings());
            // Busy-work
            for (int i = 0; i < 1_000_000_000; i++) {
                sum += i;
            }
            Thread.sleep(1000);
        }
        System.out.println(JFR.checkRecordings());
    }

}

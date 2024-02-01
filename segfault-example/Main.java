class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 10000; sum++) {
            sum += doLoopThing(123, 456 + i);
        }
        System.out.println(sum);

        while (true) {
            try {
                Thread.sleep(10000);
                System.out.println("sleeping...");
            } catch (Exception e) {
                break;
            }
        }
    }

    private static int doLoopThing(int x, int y) {
        int z = 0;
        int originalX = x;
        while (x > 0) {
            z++;
            x--;
        }
        int abc = z * y + 2;
        return abc * originalX;
    }
}
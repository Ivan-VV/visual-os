public class Computer {
    private int clock;

    Computer(){
        clock=0;
    }

    public void clockstart()throws InterruptedException{
        for(;;) {
            Thread.sleep(10);
            clock += 10;
        }
    }
}

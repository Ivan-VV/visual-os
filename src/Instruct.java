public class Instruct {//指令类
    public  int Instruc_ID;//指令序号
    public int Instruc_State;//指令的类型标志，0表示系统调用，1表示用户态计算操作，2表示PV操作
    public int Instruct_Times;//指令运行时间
    public int starttime;//指令开始执行时间
}
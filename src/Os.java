import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Stack;
import java.util.Vector;

public class Os {
    private Computer computer;//操作系统管理的裸机

    public boolean memory_table[]=new boolean[64];
    //内存页框表，下标为页框号，true表示该页框被占用，false表示该页框空闲

    public byte page_table[][]=new byte[128][2];//页表,下标为页面号，每个byte有8位
    //第一列：
    // 第0位为分配标志位，0表示该页面空闲，1表示该页面已分配给进程
    // 第1位为驻留标志位，0表示该页面不在内存，1表示该页面已经装入内存
    // 第2-7位表示该页面装入的内存块号
    //第二列：
    // 第0位为保护标志，0表示不受保护，1表示受保护
    // 第1-7位作为LRU-老化算法的页面引用计数器

    public boolean inter_flag;//中断标志位，true为中断，false不能中断

    public Job jobs[];//随机生成的作业序列
    public int job_num;
    public boolean block_flag;//当前是否有进程占用临界资源，false为否，true为是
    public boolean end_flag;//是否执行完所有作业，false为否，true为是
    private int instr_flag;//表示当前CPU正在执行的指令类型，0表示系统调用，1表示用户态计算操作，2表示PV操作
    private int pv_flag;//表示是否已有进程进行PV操作且未结束，-1表示无，非负表示占用临界资源的进程ID
    private int syn_flag;//同步标志,-1表示不需要同步，非负表示对应序号的进程不需要同步，和对应序号进程同步的进程需要同步
    private PrintStream log;//写系统日志
    private PrintStream memory_log;//写内存占用情况日志
    private int resources[][]=new int[5][5];//共有5种资源，每种资源数量为5，-1表示空闲，非负表示被对应序号进程占有
    private int resource_num[]=new int[5];//共有5种资源，数组存放每种资源的数量

    private Queue<Process>q1=new LinkedList<Process>();//运行队列（数量只能为1或0）
    private Queue<Process>q2=new LinkedList<Process>();//就绪队列
    private Queue<Process>q3=new LinkedList<Process>();//阻塞队列
    private Queue<Process>q4=new LinkedList<Process>();//已完成的进程
    private Queue<Process>q5=new LinkedList<Process>();//没有空闲页面导致尚未分配完页面需等待的进程
    private Queue<Process>q6=new LinkedList<Process>();//因造成死锁而被撤销的进程
    private Queue<Process>q_re=new LinkedList<Process>();//因为需要资源不足而阻塞的队列

    Os(Computer computer){//构造函数
        this.computer=computer;
        inter_flag=false;
        for(int i=0;i<64;i++)
            memory_table[i]=false;
        for(int i=0;i<128;i++) {
            page_table[i][0] = 0;
            page_table[i][1] = 0;
        }
        job_num=5+(int)(Math.random()*6);//随机生成5-10个作业
        jobs=new Job[job_num];
        for(int i=0;i<job_num;i++)//初始化作业序列
            jobs[i]=new Job();

        //随机生成需要进行同步的进程
        for(int i=0;i<job_num;i++)
            for (int j = 0; j < jobs[i].task_num; j++)
                jobs[i].task_list[j].syn_flag=-1;
        for(int i=0;i<job_num;i++){
            if(jobs[i].task_num>=2){
                int pro_i=0;
                for(int j=0;j<i;j++)
                    for(int k=0;k<jobs[j].task_num;k++)
                        pro_i++;
                jobs[i].task_list[1].syn_flag=pro_i;
                jobs[i].task_list[0].syn_flag=pro_i+1;
                jobs[i].task_list[0].resource_flag=false;
                jobs[i].task_list[1].resource_flag=false;
                syn_flag=pro_i;
                break;
            }
        }

        block_flag=false;
        end_flag=false;
        pv_flag=-1;

        //初始化资源数量和占有情况
        for(int i=0;i<5;i++) {
            for (int j = 0; j < 5; j++)
                resources[i][j] = -1;
            resource_num[i]=5;
        }

        //初始化日志输出流
        File directory=new File(".");
        String path=null;
        String memory_path=null;
        try{
            path=directory.getCanonicalPath();
        }catch(IOException e){
            e.printStackTrace();
        }
        memory_path=path;
        path+="\\run_log.txt";
        memory_path+="\\memory_log.txt";
        File logfile=new File(path);
        File memory_logfile=new File(memory_path);
        try {
            if (!logfile.exists())
                logfile.createNewFile();
            if(!memory_logfile.exists())
                memory_logfile.createNewFile();
            FileOutputStream out=new FileOutputStream(logfile);
            log=new PrintStream(out);
            FileOutputStream memory_out=new FileOutputStream(memory_logfile);
            memory_log=new PrintStream(memory_out);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void osstart()throws InterruptedException{//启动操作系统
        for(;;){
            synchronized (computer.clock) {
                if(!inter_flag) {//线程同步，确保每次时钟中断都进行了调度
                    computer.clock.wait();
                }
                cpu_manage();
                gui();
                inter_flag = false;
                computer.clock.notifyAll();
                if (end_flag){
                    computer.disk.write();
                    log.close();
                    break;
                }
            }
        }
    }

    public void memory_manage(Process process,int mode){//内存管理
        //mode=1，创建进程时调用，为进程分配页面，若此时有空余页框，则将页面装入内存
        //mode=2，开始执行进程时调用，每执行一条指令也要调用一次确保将执行到的指令所在页面和进程所需数据所在页面装入内存，
        //        并将逻辑地址转换为物理地址
        //mode=3，撤销进程时调用，回收进程所用内存和页面
        switch (mode){
            case 1: {
                boolean flag=true;
                for (int i = 0; i < process.page_num; i++) {
                    if(process.pages[i]==-1) {//尚未分配页面
                        flag = false;
                        for (int j = 0; j < 128; j++) {//寻找尚未分配出去的页面
                            if ((page_table[j][0] & 128) == 0) {//分配标志位为0
                                page_table[j][0] = (byte) (page_table[j][0] | 128);//将分配标志位置1
                                process.pages[i] = j;
                                for(int k=0;k<32;k++){//在硬盘中开辟缓存区存储进程
                                    boolean disk_flag=false;
                                    for(int l=0;l<64;l++){
                                        if(computer.disk.disk[k][l]==-1){
                                            computer.disk.disk[k][l]=process.ProID;
                                            disk_flag=true;
                                            break;
                                        }
                                    }
                                    if(disk_flag)
                                        break;
                                }
                                flag = true;
                                break;
                            }
                        }
                        if (flag == false) {
                            break;
                        }
                    }
                }
                if(flag==false){//没有空闲页面
                    System.out.println("没有空闲页面！需等待其他进程执行完释放页面！");
                    log.println("没有空闲页面！需等待其他进程执行完释放页面！");
                    process.ProState=4;//进程状态为等待分配页面
                    q5.offer(process);
                }else {
                    for (int i = 0; i < process.page_num; i++) {
                        boolean mem_flag = false;
                        for (int j = 0; j < 64; j++) {//寻找空闲的页框
                            if (!memory_table[j]) {//该页框空闲
                                page_table[process.pages[i]][0] = (byte) (page_table[process.pages[i]][0] | 64);
                                //将页表项的驻留标志位改为1
                                page_table[process.pages[i]][0] = (byte) (page_table[process.pages[i]][0] & 192);
                                page_table[process.pages[i]][0] += (byte) j;
                                //将页面所分得页框的页框号写入该页表项的2-7位
                                memory_table[j] = true;//表示该页框已被占用
                                mem_flag = true;
                                break;
                            }
                        }
                        if (mem_flag == false) {
                            System.out.println("没有空闲页框！");
                            log.println("没有空闲页框！");
                            break;
                        }
                    }
                    System.out.print(computer.clock.gettime()+"时刻|创建"+process.ProID+"号进程|分配页面号:");
                    log.print(computer.clock.gettime()+"时刻|创建"+process.ProID+"号进程|分配页面号:");
                    for(int i=0;i<process.page_num;i++) {
                        System.out.print(process.pages[i] + " ");
                        log.print(process.pages[i] + " ");
                    }
                    System.out.println("");
                    log.println("");
                    process.PSW=0;//从进程的第1条指令开始执行
                    process.intime=computer.clock.gettime();

                    if(process.resource_flag){//如果进程需要分配资源
                        if(allocate(process)) {
                            process.ProState = 2;//创建好的进程为就绪态
                            q2.offer(process);
                        } else{
                            process.ProState=3;//资源不够分配，进程为阻塞态
                            System.out.println(computer.clock.gettime()+"时刻|"+process.ProID+"号进程请求分配资源失败，" +
                                    "进入等待分配资源队列");
                            log.println(computer.clock.gettime()+"时刻|"+process.ProID+"号进程请求分配资源失败，" +
                                    "进入等待分配资源队列");
                            q_re.offer(process);//进程被阻塞
                        }
                    }else {//如果进程不需要分配资源
                        process.ProState = 2;//创建好的进程为就绪态
                        q2.offer(process);//创建好的进程进入就绪队列
                    }
                }
                break;
            }
            case 2: {
                int instr_add = process.PSW * 2;//当前执行到指令的逻辑地址
                int instr_page_num = instr_add / 512;//当前执行到指令的逻辑地址所在该进程分得的第几个页面
                int instr_page = process.pages[instr_page_num];//当前执行到指令的逻辑地址所在页面号
                page_table[instr_page][1] = (byte) (page_table[instr_page][1] | 128);//将该页面保护位置1

                if (process.instruc_list[process.PSW].data_flag == 1) {//若当前执行到的指令要访问数据区
                    int data_add_s = process.instrucnum * 2;//进程的数据区的起始逻辑地址
                    int data_add_e = process.data_size + data_add_s-1;//进程的数据区的结束逻辑地址
                    int data_page_s_num = data_add_s / 512;//进程的数据区起始所在该进程分得的第几个页面
                    int data_page_e_num = data_add_e / 512;//进程的数据区结束所在该进程分得的第几个页面
                    for (int i = data_page_s_num; i <= data_page_e_num; i++)//将数据区分得的几个页面保护位置1
                        page_table[process.pages[i]][1] = (byte) (page_table[process.pages[i]][1] | 128);

                    if ((page_table[instr_page][0] & 64) == 0) {//如果指令所在页面没有装入内存
                        page_in(instr_page);//将指令所在页面装入内存
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] | 64);
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] & 192);
                        //引用该页面，计数器最左位置1，计数器其他位置0
                    }
                    else//如果指令所在页面已装入内存
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] | 64);
                        //引用该页面，计数器最左位置1

                    for (int i = data_page_s_num; i <= data_page_e_num; i++) {//检查数据区分得的几个页面
                        if ((page_table[process.pages[i]][0] & 64) == 0) {//如果该页面没有装入内存
                            page_in(process.pages[i]);//将该页面装入内存
                            page_table[process.pages[i]][1] = (byte) (page_table[process.pages[i]][1] | 64);
                            page_table[process.pages[i]][1] = (byte) (page_table[process.pages[i]][1] & 192);
                            //引用该页面，计数器最左位置1，计数器其他位置0
                        }
                        else//如果指该页面已装入内存
                            page_table[process.pages[i]][1] = (byte) (page_table[process.pages[i]][1] | 64);
                            //引用该页面，计数器最左位置1
                    }

                    //确保所有页面都装入内存后解除保护，将指令所在页面和数据区所在页面保护位均置为0
                    page_table[instr_page][1] = (byte) (page_table[instr_page][1] & 127);
                    for (int i = data_page_s_num; i <= data_page_e_num; i++)
                        page_table[process.pages[i]][1] = (byte) (page_table[process.pages[i]][1] & 127);
                }
                else{//若当前执行到的指令无需访问数据区
                    if ((page_table[instr_page][0] & 64) == 0) {//如果指令所在页面没有装入内存
                        page_in(instr_page);//将指令所在页面装入内存
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] | 64);
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] & 192);
                        //引用该页面，计数器最左位置1，计数器其他位置0
                    }
                    else//如果指令所在页面已装入内存
                        page_table[instr_page][1] = (byte) (page_table[instr_page][1] | 64);
                    //引用该页面，计数器最左位置1

                    //确保所有页面都装入内存后解除保护，将指令所在页面保护位置为0
                    page_table[instr_page][1] = (byte) (page_table[instr_page][1] & 127);
                }
                break;
            }
            case 3:{
                for(int i=0;i<process.page_num;i++){
                    if((page_table[process.pages[i]][0]&64)==64){//若该页面已分配页框
                        int j=page_table[process.pages[i]][0]&63;//求出该页面分配的页框号
                        memory_table[i]=false;//释放占用页框
                        page_table[process.pages[i]][0]=(byte)(page_table[process.pages[i]][0]&191);
                        //将该的页面的驻留标志位改为0
                    }
                    page_table[process.pages[i]][0]=(byte)(page_table[process.pages[i]][0]&127);
                    //将该的页面的分配标志位改为0
                }
                break;
            }
        }
    }


    public void cpu_manage(){//处理器管理，时间片为500ms
        int nowtime=computer.clock.gettime();
        if(nowtime%1000==0)page_use_move();//每过1000ms将每个在内存中页面的引用计数器右移1位
        for(int i=0;i<job_num;i++){//为进入系统的作业创建进程
            if(jobs[i].intime==nowtime){
                for(int j=0;j<jobs[i].task_num;j++)
                    create(jobs[i].task_list[j]);
                break;
            }
        }

        //进程调度
        if(q1.size()==0&&q2.size()==0&&q3.size()==0&&nowtime>jobs[job_num-1].intime) {//当前没有进程在运行且没有进程就绪且没有进程阻塞且不会再有进程创建
            end_flag=true;
        }else{
            if(q1.size()!=0){//有进程正在运行
                if(q1.peek().resource_flag==false||computer.cpu.PC!=q1.peek().instrucnum/2||allocate(q1.peek())) {
                    //若进程不需要分配资源或分配资源成功

                    if (computer.cpu.PC == q1.peek().instrucnum) {//进程已执行完所有指令
                        destroy();//撤销进程
                    } else {
                        if (q1.peek().syn_flag == -1) {//若进程不需要同步
                            memory_manage(q1.peek(), 2);
                            instr_flag = q1.peek().instruc_list[computer.cpu.PC].Instruc_State;
                            System.out.print(computer.clock.gettime() + "时刻|执行" + q1.peek().ProID +
                                    "号进程" + computer.cpu.PC + "号指令——");
                            log.print(computer.clock.gettime() + "时刻|执行" + q1.peek().ProID +
                                    "号进程" + computer.cpu.PC + "号指令——");
                            if (instr_flag == 0) {//若执行的是系统调用指令
                                System.out.println("系统调用指令，系统处于内核态");
                                log.println("系统调用指令，系统处于内核态");
                                q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                    computer.cpu.PC++;
                                    q1.peek().PSW++;
                                    run_ready();
                                }
                            } else if (instr_flag == 1) {//若执行的是用户态计算指令
                                System.out.println("用户态计算指令，系统处于用户态");
                                log.println("用户态计算指令，系统处于用户态");
                                q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                    computer.cpu.PC++;
                                    q1.peek().PSW++;
                                }
                                int runtime = nowtime - q1.peek().starttime;
                                if (runtime >= 500) {//用完时间片
                                    System.out.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                    log.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                    run_ready();
                                }
                            } else if (instr_flag == 2) {//若执行的是PV操作指令
                                if (pv_flag == -1 || pv_flag == q1.peek().ProID) {//临界资源空闲
                                    System.out.println("PV操作指令，将临界资源分配给该进程");
                                    log.println("PV操作指令，将临界资源分配给该进程");
                                    if (pv_flag == -1) pv_flag = q1.peek().ProID;//将临界资源分配给该进程
                                    q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                    if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                        pv_flag = -1;
                                        if (q3.size() != 0) wake();
                                        computer.cpu.PC++;
                                        q1.peek().PSW++;
                                    }
                                    int runtime = nowtime - q1.peek().starttime;
                                    if (runtime >= 500) {
                                        System.out.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                        log.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                        run_ready();
                                    }
                                } else {//临界资源被占用
                                    System.out.println("PV操作指令，临界资源被其他进程占用，该进程进入阻塞队列");
                                    log.println("PV操作指令，临界资源被其他进程占用，该进程进入阻塞队列");
                                    run_wait();
                                }
                            }
                        } else if (syn_flag == q1.peek().ProID) {//若进程需要同步但暂时不用阻塞
                            memory_manage(q1.peek(), 2);
                            instr_flag = q1.peek().instruc_list[computer.cpu.PC].Instruc_State;
                            System.out.print(computer.clock.gettime() + "时刻|执行" + q1.peek().ProID +
                                    "号进程" + computer.cpu.PC + "号指令——");
                            log.print(computer.clock.gettime() + "时刻|执行" + q1.peek().ProID +
                                    "号进程" + computer.cpu.PC + "号指令——");
                            if (instr_flag == 0) {//若执行的是系统调用指令
                                System.out.println("系统调用指令，系统处于内核态");
                                log.println("系统调用指令，系统处于内核态");
                                q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                    if (computer.cpu.PC == q1.peek().instrucnum / 2 || computer.cpu.PC == q1.peek().instrucnum - 1)
                                        syn_flag = q1.peek().syn_flag;
                                    computer.cpu.PC++;
                                    q1.peek().PSW++;
                                    run_ready();
                                }
                            } else if (instr_flag == 1) {//若执行的是用户态计算指令
                                System.out.println("用户态计算指令，系统处于用户态");
                                log.println("用户态计算指令，系统处于用户态");
                                q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                    if (computer.cpu.PC == q1.peek().instrucnum / 2 || computer.cpu.PC == q1.peek().instrucnum - 1)
                                        syn_flag = q1.peek().syn_flag;
                                    computer.cpu.PC++;
                                    q1.peek().PSW++;
                                }
                                int runtime = nowtime - q1.peek().starttime;
                                if (runtime >= 500) {//用完时间片
                                    System.out.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                    log.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                    run_ready();
                                }
                            } else if (instr_flag == 2) {//若执行的是PV操作指令
                                if (pv_flag == -1 || pv_flag == q1.peek().ProID) {//临界资源空闲
                                    System.out.println("PV操作指令，将临界资源分配给该进程");
                                    log.println("PV操作指令，将临界资源分配给该进程");
                                    if (pv_flag == -1) pv_flag = q1.peek().ProID;//将临界资源分配给该进程
                                    q1.peek().instruc_list[computer.cpu.PC].needtime -= 10;
                                    if (q1.peek().instruc_list[computer.cpu.PC].needtime <= 0) {
                                        if (computer.cpu.PC == q1.peek().instrucnum / 2 || computer.cpu.PC == q1.peek().instrucnum - 1)
                                            syn_flag = q1.peek().syn_flag;
                                        pv_flag = -1;
                                        if (q3.size() != 0) wake();
                                        computer.cpu.PC++;
                                        q1.peek().PSW++;
                                    }
                                    int runtime = nowtime - q1.peek().starttime;
                                    if (runtime >= 500) {
                                        System.out.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                        log.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程用完时间片");
                                        run_ready();
                                    }
                                } else {//临界资源被占用
                                    System.out.println("PV操作指令，临界资源被其他进程占用，该进程进入阻塞队列");
                                    log.println("PV操作指令，临界资源被其他进程占用，该进程进入阻塞队列");
                                    run_wait();
                                }
                            }
                        } else {
                            System.out.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程需要和" +
                                    q1.peek().syn_flag + "号进程同步，暂停运行");
                            log.println(computer.clock.gettime() + "时刻|" + q1.peek().ProID + "号进程需要和" +
                                    q1.peek().syn_flag + "号进程同步，暂停运行");
                            run_ready();
                        }
                    }
                }else{//进程需要分配资源且分配资源失败
                    q1.peek().ProState=3;//进程为阻塞态
                    System.out.println(computer.clock.gettime()+"时刻|"+q1.peek().ProID+"号进程请求分配资源失败，" +
                            "进入等待分配资源队列");
                    log.println(computer.clock.gettime()+"时刻|"+q1.peek().ProID+"号进程请求分配资源失败，" +
                            "进入等待分配资源队列");
                    q_re.offer(q1.poll());//进程被阻塞
                    if(q2.size()!=0){
                        q1.offer(q2.poll());
                        q1.peek().starttime = computer.clock.gettime();
                        computer.cpu.PC=q1.peek().PSW;
                        q1.peek().ProState=1;//进程为运行态
                    }
                }
            }else{//当前CPU空闲
                if(q2.size()!=0){
                    q1.offer(q2.poll());
                    q1.peek().starttime = computer.clock.gettime();
                    computer.cpu.PC=q1.peek().PSW;
                    q1.peek().ProState=1;//进程为运行态
                }
            }
            deadlock();//死锁检测与撤销
        }
    }

    private void run_ready(){
        if (q2.size() != 0) {
            q1.peek().PSW = computer.cpu.PC;
            q1.peek().ProState=2;//进程变为就绪态
            q2.offer(q1.poll());
            q1.offer(q2.poll());
            q1.peek().starttime = computer.clock.gettime();
            computer.cpu.PC = q1.peek().PSW;
            if(computer.cpu.PC==q1.peek().instrucnum){//进程已执行完所有指令
                destroy();//撤销进程
            }else {
                memory_manage(q1.peek(), 2);
            }
        } else {
            q1.peek().starttime = computer.clock.gettime();
        }
    }

    private void run_wait(){
        q1.peek().PSW=computer.cpu.PC;
        q1.peek().ProState=3;//进程变为阻塞态
        q3.offer(q1.poll());
        if(q2.size()!=0){
            q1.offer(q2.poll());
            q1.peek().starttime = computer.clock.gettime();
            q1.peek().ProState=1;//进程变为运行态
            computer.cpu.PC=q1.peek().PSW;
            if(computer.cpu.PC==q1.peek().instrucnum){//进程已执行完所有指令
                destroy();//撤销进程
            }else {
                memory_manage(q1.peek(), 2);
            }
        }
    }

    public void create(Task task){//进程创建原语
        Process process=new Process();
        process.ProID=Process.num++;
        process.instrucnum=task.instrucnum;
        process.instruc_list=task.instruc_list;
        process.size=task.size;
        process.data_size=task.data_size;
        process.syn_flag=task.syn_flag;
        process.resource_flag=task.resource_flag;
        process.all_resources=task.all_resources;
        process.need_resources=task.need_resources;
        process.alloctate_flag=0;
        process.stack=new Stack();
        process.page_num = process.size % 512 == 0 ? process.size / 512 : process.size / 512 + 1;
        //求出为应进程分配的页面数量
        process.pages = new int[process.page_num];
        for(int i=0;i<process.page_num;i++)
            process.pages[i]=-1;

        memory_manage(process,1);//为进程分配页面
    }

    public void destroy(){//进程撤销原语
        memory_manage(q1.peek(),3);//收回该进程所占的内存空间
        q1.peek().outtime=computer.clock.gettime();//记录进程撤销时间
        q1.peek().ProState=5;//进程状态变为已完成

        System.out.print(computer.clock.gettime()+"时刻|撤销"+q1.peek().ProID+"号进程|回收页面号:");
        log.print(computer.clock.gettime()+"时刻|撤销"+q1.peek().ProID+"号进程|回收页面号:");
        for(int i=0;i<q1.peek().page_num;i++) {
            System.out.print(q1.peek().pages[i] + " ");
            log.print(q1.peek().pages[i] + " ");
        }
        if(q1.peek().resource_flag){//如果该进程占用了资源
            recycle(q1.peek());//回收进程占用资源
            System.out.print("|回收资源数:A"+q1.peek().all_resources[0]+",B"+q1.peek().all_resources[1]+",C"+
                    q1.peek().all_resources[2]+",D"+q1.peek().all_resources[3]+",E"+q1.peek().all_resources[4]);
            log.print("|回收资源数:A"+q1.peek().all_resources[0]+",B"+q1.peek().all_resources[1]+",C"+
                    q1.peek().all_resources[2]+",D"+q1.peek().all_resources[3]+",E"+q1.peek().all_resources[4]);
        }
        System.out.println();
        log.println();

        q4.offer(q1.poll());//将该进程放入已完成队列
        if(q5.size()!=0){//若有进程尚未分配页面
            memory_manage(q5.poll(),1);
        }
        if(q2.size()!=0){
            q1.offer(q2.poll());
            q1.peek().ProState=1;//进程变为运行态
            computer.cpu.PC=q1.peek().PSW;
            if(computer.cpu.PC==q1.peek().instrucnum){//进程已执行完所有指令
                destroy();//撤销进程
            }else {
                memory_manage(q1.peek(), 2);
            }
        }
    }

    public void lock_destroy(Process process){//因为造成死锁而撤销进程
        memory_manage(process,3);//收回该进程所占的内存空间
        process.outtime=computer.clock.gettime();//记录进程撤销时间
        process.ProState=6;//进程状态变为已撤销

        System.out.print(computer.clock.gettime()+"时刻|因产生死锁撤销"+process.ProID+"号进程|回收页面号:");
        log.print(computer.clock.gettime()+"时刻|因产生死锁撤销"+process.ProID+"号进程|回收页面号:");
        for(int i=0;i<process.page_num;i++) {
            System.out.print(process.pages[i] + " ");
            log.print(process.pages[i] + " ");
        }
        recycle(process);//回收进程占用资源
        System.out.print("|回收资源数:A"+(process.all_resources[0]-process.need_resources[0])+",B"+(process.all_resources[1]-process.need_resources[1])+
                ",C"+(process.all_resources[2]-process.need_resources[2])+",D"+(process.all_resources[3]-process.need_resources[3])+
                ",E"+(process.all_resources[4]-process.need_resources[4]));
        log.print("|回收资源数:A"+(process.all_resources[0]-process.need_resources[0])+",B"+(process.all_resources[1]-process.need_resources[1])+
                ",C"+(process.all_resources[2]-process.need_resources[2])+",D"+(process.all_resources[3]-process.need_resources[3])+
                ",E"+(process.all_resources[4]-process.need_resources[4]));
        System.out.println();
        log.println();
        q6.offer(process);//进程加入已撤销队列
    }

    public void block(Process process){//进程阻塞原语
        q3.offer(process);//将该进程放入阻塞队列
    }

    public void wake(){//进程唤醒原语
        q3.peek().ProState=2;//进程变为就绪态
        q2.offer(q3.poll());
    }

    boolean allocate(Process process){//为进程分配资源，若资源足够则分配成功，返回true，资源不够则分配失败，返回false
        if(process.alloctate_flag==0){//如果进程尚未分配过资源
            System.out.print(computer.clock.gettime()+"时刻|"+process.ProID+"号进程申请资源:");
            log.print(computer.clock.gettime()+"时刻|"+process.ProID+"号进程申请资源:");
            System.out.print("A"+process.need_resources[0]/2+",B"+process.need_resources[1]/2+",C"+process.need_resources[2]/2+
                    ",D"+process.need_resources[3]/2+ ",E"+process.need_resources[4]/2);
            log.print("A"+process.need_resources[0]/2+",B"+process.need_resources[1]/2+",C"+process.need_resources[2]/2+
                    ",D"+process.need_resources[3]/2+ ",E"+process.need_resources[4]/2);
            System.out.println();
            log.println();

            System.out.print(computer.clock.gettime()+"时刻|为"+process.ProID+"号进程分配资源:");
            log.print(computer.clock.gettime()+"时刻|为"+process.ProID+"号进程分配资源:");
            for(int i=0;i<5;i++){
                int n=process.need_resources[i]/2;
                if(resource_num[i]>=n){//如果剩余资源数量足够
                    //分配资源
                    int num=0;
                    for(int j=0;j<5;j++){
                        if(num==n) break;
                        if(resources[i][j]==-1){
                            resources[i][j]=process.ProID;
                            num++;
                        }
                    }
                    resource_num[i]-=n;
                    process.need_resources[i]-=n;
                    System.out.print((char)(i+65)+":"+n+" ");
                    log.print((char)(i+65)+":"+n+" ");
                }else{//如果剩余资源不够
                    System.out.println();
                    log.println();
                    return false;
                }
            }
            //资源分配成功
            System.out.println();
            log.println();
            process.alloctate_flag=1;
            return true;
        }else{//如果进程已经分配过资源
            System.out.print(computer.clock.gettime()+"时刻|"+process.ProID+"号进程申请资源:");
            log.print(computer.clock.gettime()+"时刻|"+process.ProID+"号进程申请资源:");
            System.out.print("A"+process.need_resources[0]+",B"+process.need_resources[1]+",C"+process.need_resources[2]+
                    ",D"+process.need_resources[3]+ ",E"+process.need_resources[4]);
            log.print("A"+process.need_resources[0]+",B"+process.need_resources[1]+",C"+process.need_resources[2]+
                    ",D"+process.need_resources[3]+ ",E"+process.need_resources[4]);
            System.out.println();
            log.println();

            System.out.print(computer.clock.gettime()+"时刻|为"+process.ProID+"号进程分配资源:");
            log.print(computer.clock.gettime()+"时刻|为"+process.ProID+"号进程分配资源:");
            for(int i=0;i<5;i++){
                int n=process.need_resources[i];
                if(resource_num[i]>=n){//如果剩余资源数量足够
                    //分配资源
                    int num=0;
                    for(int j=0;j<5;j++){
                        if(num==n) break;
                        if(resources[i][j]==-1){
                            resources[i][j]=process.ProID;
                            num++;
                        }
                    }
                    resource_num[i]-=n;
                    process.need_resources[i]-=n;
                    System.out.print((char)(i+65)+":"+n+" ");
                    log.print((char)(i+65)+":"+n+" ");
                }else{//如果剩余资源不够
                    System.out.println();
                    log.println();
                    return false;
                }
            }
            //资源分配成功
            System.out.println();
            log.println();
            process.alloctate_flag=1;
            return true;
        }
    }

    void recycle(Process process){//回收进程所占资源
        for(int i=0;i<5;i++){
            if(process.all_resources[i]!=0){//如果进程占有了该类资源
                for(int j=0;j<5;j++){
                    if(resources[i][j]==process.ProID)//如果该资源被该进程所占用
                        resources[i][j]=-1;
                }
                resource_num[i]+=process.all_resources[i]-process.need_resources[i];
            }
        }
    }

    void deadlock(){//死锁检测与撤销
        int available[]=new int[5];//当前系统资源数量
        Vector<Process>re_process=new Vector<Process>();//需要分配资源的进程序列
        Vector<Process>list_remove=new Vector<Process>();//记录可以完成的进程

        for(Process process:q_re) {
            if (allocate(process)) {//如果系统资源足够分配
                list_remove.add(process);//记录可以分配资源的进程
            }
        }
        q_re.removeAll(list_remove);//将能分配资源的进程从等待分配资源队列中移出
        for(Process process:list_remove){
            process.ProState=2;//进程状态变为就绪态
            q2.offer(process);//进程加入就绪队列
        }
        list_remove.clear();

        for(int i=0;i<5;i++)//记录当前系统的资源数量
            available[i]=resource_num[i];

        for(Process process:q1)
            if(process.resource_flag)re_process.add(process);
        for(Process process:q2)
            if(process.resource_flag)re_process.add(process);
        for(Process process:q3)
            if(process.resource_flag)re_process.add(process);
        for(Process process:q_re)
            re_process.add(process);

        //银行家算法
        boolean satisfy_flag = false;//记录是否有满足的进程
        list_remove.clear();
        for (; ; ) {
            satisfy_flag = false;
            for (Process process : re_process) {
                boolean flag = true;
                for (int i = 0; i < 5; i++) {
                    if (available[i] < process.need_resources[i]) {
                        flag = false;
                        break;
                    }
                }
                if (flag == true) {//如果系统资源能满足该进程
                    for (int i = 0; i < 5; i++) {
                        available[i] += process.all_resources[i] - process.need_resources[i];//回收该进程已占有的资源
                    }
                    satisfy_flag = true;
                    list_remove.add(process);//该进程可以完成
                }
            }
            if (satisfy_flag == false) break;//如果本次循环没有能满足的进程则中断
            re_process.removeAll(list_remove);//移除可以完成的进程
            list_remove.clear();
        }
        if(!re_process.isEmpty()){//还有无法满足的进程，即存在死锁
            Vector<Integer>nums=new Vector<Integer>();//储存需要撤销的进程序号
            for(Process process:re_process)
                nums.add(process.ProID);

            //撤销产生死锁的进程
            list_remove.clear();
            for(Process process:q3){
                if(nums.contains(process.ProID)){//如果该进程需要撤销
                    list_remove.add(process);//记录需要撤销的进程
                }
            }
            q3.removeAll(list_remove);//撤销产生死锁的进程
            for(Process process:list_remove) {
                if(pv_flag==process.ProID){
                    pv_flag=1;
                    if(!q3.isEmpty())
                        wake();
                }
                lock_destroy(process);
            }

            list_remove.clear();
            for(Process process:q2){
                if(nums.contains(process.ProID)){//如果该进程需要撤销
                    list_remove.add(process);//记录需要撤销的进程
                }
            }
            q2.removeAll(list_remove);//撤销产生死锁的进程
            for(Process process:list_remove) {
                if(pv_flag==process.ProID){
                    pv_flag=-1;
                    if(!q3.isEmpty())
                        wake();
                }
                lock_destroy(process);
            }

            list_remove.clear();
            for(Process process:q1){
                if(nums.contains(process.ProID)){//如果该进程需要撤销
                    list_remove.add(process);//记录需要撤销的进程
                }
            }
            q1.removeAll(list_remove);//撤销产生死锁的进程
            for(Process process:list_remove) {
                if(pv_flag==process.ProID){
                    pv_flag=1;
                    if(!q3.isEmpty())
                        wake();
                }
                lock_destroy(process);
            }

            list_remove.clear();
            for(Process process:q_re){
                if(nums.contains(process.ProID)){//如果该进程需要撤销
                    list_remove.add(process);//记录需要撤销的进程
                }
            }
            q_re.removeAll(list_remove);//撤销产生死锁的进程
            for(Process process:list_remove) {
                if(pv_flag==process.ProID){
                    pv_flag=1;
                    if(!q3.isEmpty())
                        wake();
                }
                lock_destroy(process);
            }
        }
    }

    void page_in(int page){//将页面号为page的页面装入，采用LRU算法
        boolean flag=false;
        for(int i=0;i<64;i++){//寻找空闲的页框
            if(!memory_table[i]) {//该页框空闲
                page_table[page][0]=(byte)(page_table[page][0]|64);
                //将页表项的驻留标志位改为1
                page_table[page][0]=(byte)(page_table[page][0]&192);
                page_table[page][0]+=(byte)i;
                //将页面所分得页框的页框号写入该页表项的2-7位
                memory_table[i]=true;//表示该页框已被占用
                flag=true;
                break;
            }
        }
        if(flag==false){//没有空闲页框，则要替换已分配的页框，采用LRU算法，即选出计数器最小的一个页面替换之
            int min=0;
            int add_min=-1;
            for(int i=0;i<128;i++){//找出最近最少使用的页面
                if((page_table[i][1]&128)==128) continue;//跳过受保护页面
                if((page_table[i][0]&64)==0) continue;//跳过没有分配页框的页面
                if(add_min==-1){
                    min=(page_table[i][1]&127);
                    add_min=i;
                }
                else if((page_table[i][1]&127)<min){
                    min=(page_table[i][1]&127);
                    add_min=i;
                }
            }
            int i=page_table[add_min][0]&63;//求出要替换掉的页面所占的页框号
            page_table[add_min][0]=(byte)(page_table[add_min][0]&191);//将要替换的页面的驻留标志位改为0
            page_table[page][0]=(byte)(page_table[page][0]|64);
            //将要装入页面的页表项的驻留标志位改为1
            page_table[page][0]=(byte)(page_table[page][0]&192);
            page_table[page][0]+=(byte)i;
            //将页面所分得页框的页框号写入该页表项的2-7位
        }
    }

    void page_use_move(){//每过1000ms将每个在内存中页面的引用计数器右移1位，在cpu_manage中调用
        for(int i=0;i<128;i++){
            if((page_table[i][0]&64)==0) continue;//跳过没有分配页框的页面
            byte flag=(byte)(page_table[i][1]&128);//保存该页面的保护位
            page_table[i][1]=(byte)(page_table[i][1]>>>1);
            page_table[i][1]=(byte)(page_table[i][1]&63);
            page_table[i][1]=(byte)(page_table[i][1]&flag);//还原保护位
        }
    }

    void gui(){//可视化显示
        show_memory();
        /*Queue<Process>temp;*/
        System.out.print(computer.clock.gettime()+"时刻|运行队列:");
        log.print(computer.clock.gettime()+"时刻|运行队列:");
        for(Process process:q1){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }
        /*for(int i=0;i<temp.size();i++)
            System.out.print(temp.poll().ProID+" ");*/
        System.out.print("|就绪队列:");
        log.print("|就绪队列:");
        for(Process process:q2){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }
        /*temp=q2;
        for(int i=0;i<temp.size();i++)
            System.out.print(temp.poll().ProID+" ");*/
        System.out.print("|阻塞队列:");
        log.print("|阻塞队列:");
        for(Process process:q3){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }
        /*temp=q3;
        for(int i=0;i<temp.size();i++)
            System.out.print(temp.poll().ProID+" ");*/
        System.out.print("|已完成进程:");
        log.print("|已完成进程:");
        for(Process process:q4){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }
        /*temp=q4;
        for(int i=0;i<temp.size();i++)
            System.out.print(temp.poll().ProID+" ");*/
        System.out.print("|等待空闲页面进程:");
        log.print("|等待空闲页面进程:");
        for(Process process:q5){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }

        System.out.print("|已撤销进程:");
        log.print("|已撤销进程:");
        for(Process process:q6){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }

        System.out.print("|等待分配资源进程:");
        log.print("|等待分配资源进程:");
        for(Process process:q_re){
            System.out.print(process.ProID+" ");
            log.print(process.ProID+" ");
        }

        System.out.print("|当前系统资源:");
        log.print("|当前系统资源:");
        System.out.print("A"+resource_num[0]+",B"+resource_num[1]+",C"+resource_num[2]+",D"+resource_num[3]+
                ",E"+resource_num[4]);
        log.print("A"+resource_num[0]+",B"+resource_num[1]+",C"+resource_num[2]+",D"+resource_num[3]+
                ",E"+resource_num[4]);

        System.out.println("");
        log.println("");
    }

    void showjobs(){
        System.out.println("共有"+job_num+"个作业");
        log.println("共有"+job_num+"个作业");
        int n=0;
        for(int i=0;i<job_num;i++){
            for(int j=0;j<jobs[i].task_num;j++)
                n++;
        }
        System.out.println("共有"+n+"个进程");
        log.println("共有"+n+"个进程");
        for(int i=0;i<job_num;i++){
            System.out.println("作业"+(i+1)+":  intime"+jobs[i].intime);
            log.println("作业"+(i+1)+":  intime"+jobs[i].intime);
            for(int j=0;j<jobs[i].task_num;j++){
                System.out.print("进程"+(j+1)+":  指令数目"+jobs[i].task_list[j].instrucnum
                +"  所需内存"+jobs[i].task_list[j].size);
                log.print("进程"+(j+1)+":  指令数目"+jobs[i].task_list[j].instrucnum
                        +"  所需内存"+jobs[i].task_list[j].size);
                if(jobs[i].task_list[j].resource_flag){
                    System.out.println("  所需资源"+jobs[i].task_list[j].all_resources[0]+jobs[i].task_list[j].all_resources[1]+
                            jobs[i].task_list[j].all_resources[2]+jobs[i].task_list[j].all_resources[3]+jobs[i].task_list[j].all_resources[4]);
                    log.println("  所需资源"+jobs[i].task_list[j].all_resources[0]+jobs[i].task_list[j].all_resources[1]+
                            jobs[i].task_list[j].all_resources[2]+jobs[i].task_list[j].all_resources[3]+jobs[i].task_list[j].all_resources[4]);
                }else{
                    System.out.println();
                    log.println();
                }
                for(int k=0;k<jobs[i].task_list[j].instrucnum;k++){
                    System.out.println("ID"+jobs[i].task_list[j].instruc_list[k].Instruc_ID
                    +"  类型"+jobs[i].task_list[j].instruc_list[k].Instruc_State+"  运行时间"
                    +jobs[i].task_list[j].instruc_list[k].Instruct_Times+"  访问数据"
                    +jobs[i].task_list[j].instruc_list[k].data_flag);
                    log.println("ID"+jobs[i].task_list[j].instruc_list[k].Instruc_ID
                            +"  类型"+jobs[i].task_list[j].instruc_list[k].Instruc_State+"  运行时间"
                            +jobs[i].task_list[j].instruc_list[k].Instruct_Times+"  访问数据"
                            +jobs[i].task_list[j].instruc_list[k].data_flag);
                }
            }
        }
    }

    void show_memory(){
        System.out.println(computer.clock.gettime() + "时刻|各进程占用内存情况");
        memory_log.println(computer.clock.gettime() + "时刻|各进程占用内存情况");
        System.out.println("{");
        memory_log.println("{");

        for (Process process : q1) {
            System.out.print(process.ProID + "号进程分配页面号:");
            memory_log.print(process.ProID + "号进程分配页面号:");
            for (int page : process.pages) {
                System.out.print(page);
                memory_log.print(page);
                if ((page_table[page][0] & 64) == 0) {
                    System.out.print("(不在内存)");
                    memory_log.print("(不在内存)");
                } else {
                    int i = page_table[page][0] & 63;
                    System.out.print("(页框号" + i + ")");
                    memory_log.print("(页框号" + i + ")");
                }
                System.out.print("  ");
                memory_log.print("  ");
            }

            System.out.println();
            memory_log.println();
        }

        for (Process process : q2) {
            System.out.print(process.ProID + "号进程分配页面号:");
            memory_log.print(process.ProID + "号进程分配页面号:");
            for (int page : process.pages) {
                System.out.print(page);
                memory_log.print(page);
                if ((page_table[page][0] & 64) == 0) {
                    System.out.print("(不在内存)");
                    memory_log.print("(不在内存)");
                } else {
                    int i = page_table[page][0] & 63;
                    System.out.print("(页框号" + i + ")");
                    memory_log.print("(页框号" + i + ")");
                }
                System.out.print("  ");
                memory_log.print("  ");
            }

            System.out.println();
            memory_log.println();
        }

        for (Process process : q3) {
            System.out.print(process.ProID + "号进程分配页面号:");
            memory_log.print(process.ProID + "号进程分配页面号:");
            for (int page : process.pages) {
                System.out.print(page);
                memory_log.print(page);
                if ((page_table[page][0] & 64) == 0) {
                    System.out.print("(不在内存)");
                    memory_log.print("(不在内存)");
                } else {
                    int i = page_table[page][0] & 63;
                    System.out.print("(页框号" + i + ")");
                    memory_log.print("(页框号" + i + ")");
                }
                System.out.print("  ");
                memory_log.print("  ");
            }

            System.out.println();
            memory_log.println();
        }

        System.out.println("}");
        memory_log.println("}");
    }

    int get_psw(int pro_id){
        for(Process process:q1){
            if(process.ProID==pro_id)
                return process.PSW;
        }
        for(Process process:q2){
            if(process.ProID==pro_id)
                return process.PSW;
        }
        for(Process process:q3){
            if(process.ProID==pro_id)
                return process.PSW;
        }
        for(Process process:q4){
            if(process.ProID==pro_id)
                return process.PSW;
        }
        for(Process process:q5){
            if(process.ProID==pro_id)
                return process.PSW;
        }
        return -1;
    }
}

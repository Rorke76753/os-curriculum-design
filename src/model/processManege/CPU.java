package model.processManege;

import javafx.application.Platform;
import model.deviceManage.DeviceAllocation;
import model.memoryManage.MemoryManage;
import util.StringUtil;
import view.processManagement.ProcessManagementController;
import view.processManagement.ProcessManagementWindow;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CPU {
    //时间片长度
    public static int timeSliceLength = 3;
    //申请使用设备时休眠时间
    public static int IO_BLOCK_TIME = 6000;
    //一条指令执行后休眠的时间（显示中间结果）
    public static int SLEEP_TIME_FOR_EACH_INSTRUCTMENT = 2000;

    //系统时间
    private static int systemTime = 0;
    //程序状态字
    private static PSW psw = PSW.getPSW();
    //寄存器
    private static Integer reg[] = new Integer[4];

    //线程池
    private static ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    private CPU() {
        run();
    }

    // cpu执行进程调度
    public void run() {
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    PCB pcb = getReadyProcessPCB();
                    if (pcb == null) {
                        System.out.println("就绪队列为空");
                        try {
                            showData(null);
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        psw.initPSW();
                        while (!psw.isProcessEnd() && !psw.isIOInterrupt() && !psw.isTimeSliceUsedUp()) {
                            try {
                                Thread.sleep(SLEEP_TIME_FOR_EACH_INSTRUCTMENT);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            //取指令并将指令指针 +1
                            String currentInstruction = pcb.getCurrentInstruction();
                            pcb.increaseCurrentInstructionIndex();

                            //执行并保存中间结果
                            executeInstruction(currentInstruction, pcb);

                            //剩余时间片减一，修改PSW
                            pcb.decreaseRestTime();
                            psw.setTimeSliceUsedUp(pcb.isTimeSliceUsedUp());
                            psw.setProcessEnd(pcb.isProcessEnd());

                            showData(pcb);
                            //检测并处理异常
                            handleInterrupt(pcb);
                            System.out.println(pcb);
                        }
                    }
                }
            }

            //调度就绪队列执行
            private PCB getReadyProcessPCB() {
                List<PCB> readyProcessPCBList = PCB.getReadyProcessPCBList();
                PCB pcb = null;
                if (!readyProcessPCBList.isEmpty()) {
                    pcb = readyProcessPCBList.get(0);
                    readyProcessPCBList.remove(0);

                    pcb.setProcessState(PCB.EXECUTING);
                    pcb.resetRestTime();
                    reg = pcb.readRegister();
                }
                return pcb;
            }

            //解析并执行指令
            private void executeInstruction(String instruction, PCB pcb) {
                ++CPU.systemTime;
//                if (instruction.startsWith("000")) {
//                    //执行end
//                    pcb.setProcessState(PCB.END);
////                    System.out.println(pcb.getProcessState());
////                    System.out.println(pcb);
//                    pcb.setIntermediateResult("程序执行结束");
//                } else if (instruction.startsWith("001")) {
//                    //执行x++
//                    String i = instruction.substring(3, 5);
//                    int regNum = StringUtil.parseBinaryToDecimal(Integer.parseInt(i));
//                    reg[regNum]++;
//                    pcb.setIntermediateResult("reg" + regNum + " = " + reg[regNum]);
//                } else if (instruction.startsWith("010")) {
//                    //执行x--
//                    String i = instruction.substring(3, 5);
//                    int regNum = StringUtil.parseBinaryToDecimal(Integer.parseInt(i));
//                    reg[regNum]--;
//                    pcb.setIntermediateResult("reg" + regNum + " = " + reg[regNum]);
//                } else if (instruction.startsWith("100")) {
//                    //申请控制设备
//                    String i = instruction.substring(3, 5);
//                    int equipmentNum = StringUtil.parseBinaryToDecimal(Integer.parseInt(i));
//                    //发起申请设备信号
//                    // *************
//                    // *************
//                    // *************
//                    pcb.setIntermediateResult("进程" + pcb.getProcessID() + "申请设备" + equipmentNum);
//                    psw.setIOInterrupt(true);
//                } else if (instruction.startsWith("110")) {
//                    //给x赋值
//                    int regIndex = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(3, 5)));
//                    int value = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(5)));
//                    reg[regIndex] = value;
//                    pcb.setIntermediateResult("reg" + regIndex + " = " + value);
//                } else {
//                    pcb.setIntermediateResult("指令错误！");
//                }
                switch (instruction.substring(0, 3)) {
                    case "000":
                        //执行end
                        pcb.setProcessState(PCB.END);
                        pcb.setProcessBlockReason(PCB.END);
                        pcb.setIntermediateResult("程序执行结束");
                        break;
                    case "001":
                        //申请控制设备
                        String i = instruction.substring(3, 5);
                        String equipmentNum = StringUtil.parseDeviceID(StringUtil.parseBinaryToDecimal(Integer.parseInt(i)));
                        //发起申请设备信号
                        DeviceAllocation.allocate(pcb, equipmentNum, CPU.IO_BLOCK_TIME);
                        pcb.setIntermediateResult("进程" + pcb.getProcessID() + "申请设备" + equipmentNum);
                        psw.setIOInterrupt(true);
                        break;
                    case "010":
                        //存值
                        int regNum = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(3, 5)));
                        int memAddress = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(5)));
                        //存值代码
                        MemoryManage.storeValue(memAddress, reg[regNum]);
                        pcb.setIntermediateResult("mem[" + memAddress + "]" + " = " + "reg" + regNum + "(" + reg[regNum] + ")");
                        break;
                    case "011":
                        //取值
                        regNum = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(3, 5)));
                        memAddress = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(5)));
                        //取值代码
                        int value = MemoryManage.getValue(memAddress);
                        reg[regNum] = value;
                        pcb.setIntermediateResult("reg" + regNum + " = " + value + "(mem[" + memAddress + "])");
                        break;
                    case "100":
                        //赋值指令
                        int regIndex = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(3, 5)));
                        value = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(5)));
                        reg[regIndex] = value;
                        pcb.setIntermediateResult("reg" + regIndex + " = " + value);
                        break;
                    case "110":
                        //运算指令
                        int regA = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(3, 5)));
                        int regB = StringUtil.parseBinaryToDecimal(Integer.parseInt(instruction.substring(6)));
                        char op = instruction.charAt(5);
                        if (op == '0') {
                            reg[regA] += reg[regB];
                            pcb.setIntermediateResult("reg" + regA + " = " + "reg" + regA + " + " + "reg" + regB);
                        } else {
                            reg[regA] -= reg[regB];
                            pcb.setIntermediateResult("reg" + regA + " = " + "reg" + regA + " - " + "reg" + regB);
                        }
                        break;
                    default:
                        System.out.println("指令错误！");
                        pcb.setIntermediateResult("指令错误！");
                        break;
                }
            }

            //检测并处理中断
            private void handleInterrupt(PCB pcb) {
                if (psw.isProcessEnd()) {
                    ProcessControl.destroy(pcb);
                } else if (psw.isIOInterrupt()) {
                    //执行IO操作，进入阻塞队列，IO_BLOCK_TIME时间后从阻塞队列回到就绪队列
                    ProcessControl.block(pcb, PCB.IO_INTERRUPT);
                    /*
                    XXX
                    模拟执行io操作，交由设备管理实现
                     */
//                    cachedThreadPool.submit(new Runnable() {
//                        @Override
//                        public void run() {
//                            //进程休眠模拟执行IO操作
//                            try {
//                                Thread.sleep(IO_BLOCK_TIME);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                            ProcessControl.awake(pcb);
//                        }
//                    });
                } else if (psw.isTimeSliceUsedUp()) {
                    pcb.resetRestTime();
                    PCB.getReadyProcessPCBList().add(pcb);
                }
            }
        });
    }

    public static int getSystemTime() {
        return systemTime;
    }

    public static ExecutorService getThreadPool() {
        return cachedThreadPool;
    }

    public static Integer[] getRegisters() {
        return reg;
    }

    private void showData(PCB pcb) {
        ProcessManagementController controller = ProcessManagementWindow.getController();
        if (controller != null) {
            Platform.runLater(new Runnable() {
                                  @Override
                                  public void run() {
                                      controller.updateData(pcb.getProcessID() == null? null : pcb);
                                  }
                              }
            );
        }
    }

    //关机
    public static void shutdown(){
        cachedThreadPool.shutdown();
    }
    //测试方法
    public static void work() {
        ProcessControl.create(new File("resources/test.e"));
        ProcessControl.create(new File("resources/test.e"));
        ProcessControl.create(new File("resources/test.e"));
        ProcessControl.create(new File("resources/test.e"));
        CPU cpu = new CPU();
    }
}
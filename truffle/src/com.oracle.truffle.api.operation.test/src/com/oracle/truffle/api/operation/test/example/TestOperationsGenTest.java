package com.oracle.truffle.api.operation.test.example;

import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;

@RunWith(JUnit4.class)
public class TestOperationsGenTest {

    private static class OperationsRootNode extends RootNode {

        @Child private OperationsNode executable;

        protected OperationsRootNode(OperationsNode executable) {
            super(null);
            this.executable = executable;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return this.executable.execute(frame);
        }

    }

    private static void parseAdd(SlOperationsBuilderino b) {
        // simple test:
        // function foo(a, b) {
        // return (a + b);
        // }
        b.beginReturn();
        b.beginAddOperation();
        b.emitLoadArgument((short) 0);
        b.emitLoadArgument((short) 1);
        b.endAddOperation();
        b.endReturn();
    }

    private static void parseMax(SlOperationsBuilderino b) {
        // control flow test:
        // function max(a, b) {
        // if (a < b) {
        // return b;
        // } else {
        // return a;
        // }
        b.beginIfThenElse();

        b.beginLessThanOperation();
        b.emitLoadArgument((short) 0); // a
        b.emitLoadArgument((short) 1); // b
        b.endLessThanOperation();

        b.beginReturn();
        b.emitLoadArgument((short) 1); // b
        b.endReturn();

        b.beginReturn();
        b.emitLoadArgument((short) 0); // a
        b.endReturn();

        b.endIfThenElse();
    }

    @Test
    public void testAdd() {
        runTest(TestOperationsGenTest::parseAdd, 42L, 20L, 22L);
        runTest(TestOperationsGenTest::parseAdd, "foobar", "foo", "bar");
    }

    @Test
    public void testMax() {
        runTest(TestOperationsGenTest::parseMax, 42L, 42L, 13L);
        runTest(TestOperationsGenTest::parseMax, 42L, 13L, 42L);
    }

    @Test
    public void testSumLoop() {
        runTest(TestOperationsGenTest::parseSumLoop, 45L, 10L);
    }

    @Test
    public void testBreakLoop() {
        runTest(TestOperationsGenTest::parseBreakLoop, 6L, 5L);
        runTest(TestOperationsGenTest::parseBreakLoop, 10L, 15L);
    }

    private static void runTest(Consumer<SlOperationsBuilderino> parse, Object expectedResult, Object... args) {
        System.out.println("------------------------------------");
        SlOperationsBuilderino b = SlOperationsBuilderino.createBuilder();
        parse.accept(b);
        System.out.println(" building");
        OperationsNode executable = b.build();
        System.out.println(" dumping");
        System.out.println(executable.dump());
        b.reset();
        System.out.println(executable);
        CallTarget target = new OperationsRootNode(executable).getCallTarget();
        Object result = target.call(args);
        Assert.assertEquals(expectedResult, result);
    }

    private static void parseSumLoop(SlOperationsBuilderino b) {
        // control flow test:
        // function sum(length) {
        // sum = 0;
        // i = 0;
        // while (i < length) {
        // sum += i;
        // i += 1;
        // }
        // return sum;

        b.beginStoreLocal((short) 0); // sum
        b.emitConstObject(0L);
        b.endStoreLocal();

        b.beginStoreLocal((short) 1); // i
        b.emitConstObject(0L);
        b.endStoreLocal();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal((short) 1); // i
        b.emitLoadArgument((short) 0); // length
        b.endLessThanOperation();

        b.beginBlock();

        b.beginStoreLocal((short) 0); // sum
        b.beginAddOperation();
        b.emitLoadLocal((short) 0);
        b.emitLoadLocal((short) 1); // i
        b.endAddOperation();
        b.endStoreLocal();

        b.beginStoreLocal((short) 1);
        b.beginAddOperation();
        b.emitLoadLocal((short) 1);
        b.emitConstObject(1L);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal((short) 0);
        b.endReturn();
    }

    private static void parseBreakLoop(SlOperationsBuilderino b) {
        // function breakLoop(input) {
        // i = 0;
        // while (i < 10) {
        // if (input < i) break;
        // i += 1;
        // }
        // return i;

        b.beginStoreLocal((short) 0); // i
        b.emitConstObject(0L);
        b.endStoreLocal();

        OperationLabel breakLbl = b.createLabel();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal((short) 0); // i
        b.emitConstObject(10L);
        b.endLessThanOperation();

        b.beginBlock();

        b.beginIfThen();

        b.beginLessThanOperation();
        b.emitLoadArgument((short) 0); // input
        b.emitLoadLocal((short) 0); // i
        b.endLessThanOperation();

        b.emitBranch(breakLbl);

        b.endIfThen();

        b.beginStoreLocal((short) 0);
        b.beginAddOperation();
        b.emitLoadLocal((short) 0);
        b.emitConstObject(1L);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.emitLabel(breakLbl);

        b.beginReturn();
        b.emitLoadLocal((short) 0);
        b.endReturn();
    }

}

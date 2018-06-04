import com.amd.aparapi.Kernel;
import com.amd.aparapi.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class Board {

    final static int SIZE = 4;
    final static int MOVE_LEFT = 0;
    final static int MOVE_RIGHT = 180;
    final static int MOVE_UP = -90;
    final static int MOVE_DOWN = 90;
    final static int DEPTH = 1;
    final static Random RAND;

    static {
        RAND = new Random();
    }

    int[][] board;

    public Board(){
        board = new int[SIZE][SIZE];
    }

    public Board(int[][] existingBoard){
        super();
        clobber(existingBoard);
    }

    public static void fillRandom(int[][] board){
        int fill = RAND.nextDouble() < .1 ? 4 : 2;
        ArrayList<int[]> emptyLocations = new ArrayList<>();
        boolean done = false;
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                if(board[r][c] == 0){
                    int[] loc = {r,c};
                    emptyLocations.add(loc);
                }

            }
        }
        int[] fillLoc = emptyLocations.get(RAND.nextInt(emptyLocations.size()));
        board[fillLoc[0]][fillLoc[1]] = fill;
    }

    public static void findNextMove(int[][] board){
        //left
        int[][] left = condense(board);
        int[][][] tempLeft = {left};
        for(int i = 0; i < DEPTH; i++){

            int[][][] leftBoards2 = fillEmpty(tempLeft,2);
            int[] left2rotations = rotatePermutationsGPU(leftBoards2);
            condenseGPU(leftBoards2);
            unRotatePermutationsGPU(leftBoards2,left2rotations);

            int[][][] leftBoards4 = fillEmpty(tempLeft,4);
            int[] left4rotations = rotatePermutationsGPU(leftBoards4);
            condenseGPU(leftBoards2);
            unRotatePermutationsGPU(leftBoards2,left4rotations);

            double average2 = average(leftBoards2);
            double average4 = average(leftBoards4);
            double leftAverage = average2 * .9 + average4 * .1;
        }

    }

    public static double average (int[][][] boards){
        int[] sum = new int[1];

        Kernel sumKernel = new Kernel() {
            @Override
            public void run() {
                sum[0] += heuristic(boards[getGlobalId()]);
            }
        } ;
        sumKernel.execute(Range.create(boards.length));
        return sum[0] / boards.length;
    }

    public static void condenseGPU(int[][][] boards){
        Kernel condenseKernel = new Kernel() {
            @Override
            public void run() {
                condense(boards[getGlobalId()]);
            }
        } ;
        condenseKernel.execute(Range.create(boards.length));
    }

    public void move(int[][] board, final int moveDirection){
        Kernel moveKernel = new Kernel() {
            @Override
            public void run() {
                if(moveDirection != MOVE_LEFT){
                    clobber(rotate(board,moveDirection));
                }
                Kernel condenseKernel = new Kernel() {
                    @Override
                    public void run() {
                        condenseRow(board[getGlobalId()]);
                    }
                };
                condenseKernel.execute(Range.create(3));
                if(moveDirection != MOVE_LEFT){
                    clobber(rotate(board,-moveDirection));
                }
            }
        };
    }
    public static void fill(int[][] board, int r, int c, int value){
        board[r][c] = value;
    }
    public static int[][][] fillEmpty(int[][][] boards, int value){
        ArrayList<int[][]> results = new ArrayList<>();
        for(int k = 0; k < boards.length; k++){
            int[][] board = boards[k];
            for (int r = 0; r < board.length; r++) {
                for (int c = 0; c < board[r].length; c++) {
                    if(board[r][c] == 0){
                        fill(board,r,c,value);
                        results.add(board);
                    }
                }
            }
        }
        return results.toArray(new int[0][0][0]);

    }

    public static int[] rotatePermutationsGPU(int[][][] boards){


        int[] rotation = new int[boards.length*4];
        int[][][] rotated = new int[boards.length*4][][];
        Kernel rotateKernel = new Kernel() {
            @Override
            public void run() {
                int i = getGlobalId();
                int[][] left = rotate(boards[i],MOVE_LEFT);
                int[][] right = rotate(boards[i],MOVE_RIGHT);
                int[][] up = rotate(boards[i],MOVE_UP);
                int[][] down = rotate(boards[i],MOVE_DOWN);
                for(int j = 0; j <SIZE; j++){
                    rotated[i] = left;
                    rotated[i+1] = right;
                    rotated[i+2] = up;
                    rotated[i+3] = down;
                    rotation[i] = MOVE_LEFT;
                    rotation[i+1] = MOVE_RIGHT;
                    rotation[i+2] = MOVE_UP;
                    rotation[i+3] = MOVE_DOWN;
                }
            }
        } ;
        rotateKernel.execute(Range.create(boards.length));
        return rotation;
    }
    public static void rotatePermutations(int[][][] boards, int[] rotation){
        int[][][] rotated = new int[boards.length*4][][];
        rotation = new int[boards.length*4];
        for (int i = 0; i < boards.length; i++) {
            int[][] left = rotate(boards[i],MOVE_LEFT);
            int[][] right = rotate(boards[i],MOVE_RIGHT);
            int[][] up = rotate(boards[i],MOVE_UP);
            int[][] down = rotate(boards[i],MOVE_DOWN);
            for(int j = 0; j <SIZE; j++){
                rotated[i] = left;
                rotated[i+1] = right;
                rotated[i+2] = up;
                rotated[i+3] = down;
                rotation[i] = MOVE_LEFT;
                rotation[i+1] = MOVE_RIGHT;
                rotation[i+2] = MOVE_UP;
                rotation[i+3] = MOVE_DOWN;
            }
        }
    }

    public static void unRotatePermutationsGPU(int[][][] boards, int[] rotation){
        Kernel unRotateKernel = new Kernel() {
            @Override
            public void run() {
                int i = getGlobalId();
                boards[i] = rotate(boards[i],-1 * rotation[i]);
            }
        } ;
        unRotateKernel.execute(Range.create(boards.length));
    }

    public static void unRotatePermutations(int[][][] boards, int[] rotation){
        for (int i = 0; i < boards.length; i++) {
            boards[i] = rotate(boards[i],-1 * rotation[i]);
        }
    }

    public static int[] permutationHeuristics(int[][][] boards){
        int[] heuristics = new int[boards.length];
        for (int i = 0; i < boards.length; i++) {
            heuristics[i] = heuristic(boards[i]);
        }
        return heuristics;
    }

    public static int heuristic(int[][] board){
        int heuristic = 0;
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                if(board[r][c] != 0){
                    heuristic += board[r][c] * board[r][c];
                }
            }
        }
        return heuristic;
    }

    public static int[][] rotate(int[][] board, int moveDirection){
        double angle = Math.toRadians(moveDirection);
        int[][] temp = new int[SIZE][SIZE];
        for(int r = 0; r < SIZE; r++){
            for (int c = 0; c < SIZE; c++) {
                int newR = (int)(r * Math.cos(angle) - c * Math.sin(angle));
                int newC = (int)(r * Math.sin(angle) + c * Math.cos(angle));
                temp[newR][newC] = board[r][c];
            }
        }
        return temp;
    }

    public void clobber(int[][] newBoard){
        for (int r  = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = newBoard[r][c];
            }
        }
    }

    public static int[][] condense(int[][] board){
        for (int r = 0; r < board.length; r++) {
            condenseRow(board[r]);
        }
        return board;
    }



    public static void condenseRow(int[] row){
        for (int i = 0; i < SIZE - 1; i++) {
            if(row[i] == 0){
                int j;
                for(j = i+1; j < SIZE; j++){
                    if(row[j] != 0)
                        break;
                }
                if(j >= SIZE || row[j] == 0)
                    break;
                row[i] = row[j];
                row[j] = 0;
                i--;
            }
            else {
                int j;
                for (j = i + 1; j < SIZE; j++) {
                    if (row[j] != 0)
                        break;
                }
                if (j < SIZE && row[i] == row[j]) {
                    row[i] = row[i] * 2;
                    row[j] = 0;
                }
            }
        }
    }

    @Override
    public String toString() {
        return Arrays.deepToString(board);
    }
}

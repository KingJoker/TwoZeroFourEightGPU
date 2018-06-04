public class Main {

    public static void main(String[] args) {
        int[][] board = new int[Board.SIZE][Board.SIZE];
        Board.fillRandom(board);
        Board.findNextMove(board);

    }
}

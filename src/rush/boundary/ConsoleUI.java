package rush.boundary;

import java.util.List;
import java.util.Scanner;

import rush.entity.ComboMeal;
import rush.entity.Customer;
import rush.entity.CustomerStatus;
import rush.entity.HappyHour;
import rush.entity.MenuItem;
import rush.entity.Money;
import rush.entity.Order;
import rush.entity.Restaurant;
import rush.entity.Table;

/**
 * Command-line interface. It only prints the game state and reads the
 * manager's input; all game rules are in the control and entity classes.
 */
public class ConsoleUI {

    private Scanner scanner = new Scanner(System.in);
    private boolean echo; // print the input back, so replayed scripts are readable

    public ConsoleUI(boolean echo) {
        this.echo = echo;
    }

    public void showWelcome(Restaurant restaurant, int maxTurns) {
        System.out.println("==================================================");
        System.out.println(" RESTAURANT RUSH - you are the manager");
        System.out.println("==================================================");
        System.out.println(" " + maxTurns + " turns, " + restaurant.getTables().size()
                + " tables, 1 Host, 1 Waiter, 1 Chef, 1 Cashier.");
        System.out.println(" Menu:");
        for (MenuItem item : restaurant.getMenu().getDishes()) {
            System.out.printf("   %-21s %7s  %d prep unit(s)%n", item.getName(),
                    Money.format(item.getPriceCents()), item.getPrepUnits());
        }
        for (ComboMeal combo : restaurant.getMenu().getCombos()) {
            System.out.printf("   %-21s %7s  %d prep unit(s)%n", combo.getName(),
                    Money.format(combo.getPriceCents()), combo.getPrepUnits());
        }
        System.out.println(" A customer with satisfaction " + Customer.COMBO_SATISFACTION
                + " or more when ordering upgrades to a combo.");
        System.out.println(" Happy Hour (once per game, this turn and the next): 20% off orders taken,");
        System.out.println(" and waiting customers recover " + HappyHour.RECOVERY + " satisfaction each turn.");
        System.out.println(" Goal: at least 4 paid customers, revenue at least $45.00 and");
        System.out.println("       average satisfaction of paid customers at least 60.");
    }

    public void showTurnHeader(int turn, int maxTurns) {
        System.out.println();
        System.out.println("---------------- TURN " + turn + " / " + maxTurns + " ----------------");
    }

    public void showTurnState(Restaurant restaurant) {
        System.out.println(" Revenue: " + Money.format(restaurant.getRevenueCents())
                + " | Avg satisfaction (paid): " + formatAverage(restaurant.getAverageSatisfaction()));
        HappyHour happyHour = restaurant.getHappyHour();
        if (happyHour.isActive()) {
            System.out.println(" Happy Hour: ON, " + happyHour.getTurnsLeft() + " turn(s) left including this one");
        } else {
            System.out.println(" Happy Hour: " + (happyHour.isAvailable() ? "available" : "used"));
        }

        System.out.println(" Waiting queue:");
        if (restaurant.getWaitingQueue().isEmpty()) {
            System.out.println("   (empty)");
        }
        for (Customer c : restaurant.getWaitingQueue()) {
            System.out.println("   " + c + "  satisfaction " + c.getSatisfaction()
                    + "  (loses " + c.getSatisfactionLoss() + " per waiting turn)");
        }

        System.out.println(" Tables:");
        for (Table t : restaurant.getTables()) {
            if (t.isFree()) {
                System.out.println("   Table " + t.getNumber() + ": free");
            } else {
                Customer c = t.getCustomer();
                String order = (c.getOrder() == null) ? "no order yet" : c.getOrder().toString();
                System.out.println("   Table " + t.getNumber() + ": " + c + "  satisfaction "
                        + c.getSatisfaction() + "  " + c.getStatus() + "  " + order);
            }
        }
        showStaffTasks(restaurant);
    }

    private void showStaffTasks(Restaurant restaurant) {
        System.out.println(" Staff tasks available:");
        String seat = restaurant.findFreeTable() == null ? "-" : listCustomers(restaurant.getWaitingQueue());
        System.out.println("   Host:    seat " + seat);
        System.out.println("   Waiter:  1) seat " + seat
                + " | 2) take order " + listCustomers(restaurant.getCustomersWithStatus(CustomerStatus.SEATED))
                + " | 3) serve " + listCustomers(restaurant.getCustomersReadyToServe()));
        System.out.println("   Chef:    prepare " + listOrders(restaurant.getOrdersInKitchen()));
        Customer next = restaurant.nextToPay();
        System.out.println("   Cashier: automatic, next payment " + (next == null ? "-" : "C" + next.getId()));
    }

    private String listCustomers(List<Customer> customers) {
        if (customers.isEmpty()) {
            return "-";
        }

        StringBuilder text = new StringBuilder();

        for (Customer c : customers) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append("C").append(c.getId());
        }
        return text.toString();
    }

    private String listOrders(List<Order> orders) {
        if (orders.isEmpty()) {
            return "-";
        }

        StringBuilder text = new StringBuilder();

        for (Order o : orders) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append("C").append(o.getCustomer().getId())
                .append(" ").append(o.getItem().getName())
                .append(" ").append(o.getProgress());
        }
        return text.toString();
    }
    
    public void showMessage(String message) {
        System.out.println("  > " + message);
    }

    public void showError(String message) {
        System.out.println("  ! Not allowed: " + message + " Please choose again.");
    }

    /** Reads a whole number between min and max, asking again until the input is valid. */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(" " + prompt);
            String line = readLine();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // handled below
            }
            System.out.println("  ! Please enter a number from " + min + " to " + max + ".");
        }
    }

    public boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(" " + prompt + " (y/n): ");
            String line = readLine().toLowerCase();
            if (line.equals("y") || line.equals("yes")) {
                return true;
            }
            if (line.equals("n") || line.equals("no")) {
                return false;
            }
            System.out.println("  ! Please enter y or n.");
        }
    }

    /** Reads one line. In a script, text after '#' is a comment and comment-only lines are skipped. */
    private String readLine() {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().startsWith("#")) {
                continue;
            }
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf('#'));
            }
            line = line.trim();
            if (echo) {
                System.out.println(line);
            }
            return line;
        }
        System.out.println();
        System.out.println("No more input - game stopped.");
        System.exit(0);
        return "";
    }

    public void showEndOfTurn(int turn, int maxTurns, Restaurant restaurant) {
        System.out.println(" End of turn " + turn + ": served " + restaurant.getServedCount()
                + " | unhappy departures " + restaurant.getLeftCount()
                + " | revenue " + Money.format(restaurant.getRevenueCents())
                + " | avg satisfaction " + formatAverage(restaurant.getAverageSatisfaction())
                + " | turns used " + turn + "/" + maxTurns);
    }

    public void showResult(boolean victory, Restaurant restaurant, String[] targetLines) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(victory ? " VICTORY! All targets met." : " DEFEAT. Not every target was met.");
        System.out.println("==================================================");
        for (String line : targetLines) {
            System.out.println(" " + line);
        }
        System.out.println(" Served: " + restaurant.getServedCount()
                + " | Unhappy departures: " + restaurant.getLeftCount());
    }

    public String formatAverage(double average) {
        if (average < 0) {
            return "N/A";
        }
        return String.format("%.1f", average);
    }
}

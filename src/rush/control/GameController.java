package rush.control;

import rush.boundary.ConsoleUI;
import rush.entity.Cashier;
import rush.entity.Chef;
import rush.entity.CriticCustomer;
import rush.entity.Customer;
import rush.entity.HappyHour;
import rush.entity.Host;
import rush.entity.InvalidActionException;
import rush.entity.Money;
import rush.entity.Order;
import rush.entity.OrderStatus;
import rush.entity.RegularCustomer;
import rush.entity.Restaurant;
import rush.entity.VIPCustomer;
import rush.entity.Waiter;

/** Runs the 18 turns in the fixed order of steps and checks the result at the end. */
public class GameController {

    public static final int MAX_TURNS = 18;
    public static final int TABLES = 2;
    public static final int[] ARRIVAL_TURNS = {1, 4, 7, 10, 13, 16};

    public static final int TARGET_PAID = 4;
    public static final int TARGET_REVENUE_CENTS = 4500;
    public static final int TARGET_SATISFACTION = 60;

    private final Restaurant restaurant = new Restaurant(TABLES);
    private final Host host = new Host();
    private final Waiter waiter = new Waiter();
    private final Chef chef = new Chef();
    private final Cashier cashier = new Cashier();
    private final ConsoleUI ui;
    private int turn;
    private int nextCustomerId = 1;

    public GameController(ConsoleUI ui) {
        this.ui = ui;
    }

    public void play() {
        ui.showWelcome(restaurant, MAX_TURNS);
        for (turn = 1; turn <= MAX_TURNS; turn++) {
            playTurn();
        }
        showResult();
    }

    private void playTurn() {
        host.startTurn();
        waiter.startTurn();
        chef.startTurn();
        cashier.startTurn();
        ui.showTurnHeader(turn, MAX_TURNS);

        // Step 1: arrivals
        if (isArrivalTurn(turn)) {
            Customer customer = createCustomer(nextCustomerId++);
            restaurant.addCustomer(customer);
            ui.showMessage(customer + " arrives and joins the waiting queue (satisfaction 100).");
        }
        ui.showTurnState(restaurant);
        happyHourStep();

        // Steps 2 and 3: manager choices. The Host seats first, the Waiter acts before the Chef.
        hostStep();
        waiterStep();
        chefStep();

        // Step 4: payment and eating. Paying first means a customer who becomes
        // READY_TO_PAY this turn pays next turn at the earliest.
        Customer paid = cashier.collectPayment(restaurant);
        if (paid != null) {
            ui.showMessage("Cashier collects " + Money.format(paid.getOrder().getPriceCents()) + " from "
                    + paid + ". PAID with satisfaction " + paid.getSatisfaction() + ", table freed.");
        }
        for (Customer c : restaurant.finishEating(turn)) {
            ui.showMessage(c + " finishes eating and is READY_TO_PAY.");
        }

        // Step 5: waiting and abandonment
        for (Customer c : restaurant.applyWaitingLoss()) {
            ui.showMessage(c + " reached satisfaction 0 and leaves without paying.");
        }
        restaurant.getHappyHour().endTurn();
        ui.showEndOfTurn(turn, MAX_TURNS, restaurant);
    }

    private boolean isArrivalTurn(int turn) {
        for (int t : ARRIVAL_TURNS) {
            if (t == turn) {
                return true;
            }
        }
        return false;
    }

    /** The arrival line-up: C2 (turn 4) is a Critic, C3 (turn 7) is a VIP, the rest are Regular. */
    private Customer createCustomer(int id) {
        if (id == 2) {
            return new CriticCustomer(id, turn);
        }
        if (id == 3) {
            return new VIPCustomer(id, turn);
        }
        return new RegularCustomer(id, turn);
    }

    /** At the start of a turn the manager may switch on Happy Hour (once per game). */
    private void happyHourStep() {
        HappyHour happyHour = restaurant.getHappyHour();
        if (happyHour.isAvailable() && ui.readYesNo("Start Happy Hour now? Lasts this turn and the next")) {
            try {
                happyHour.activate();
                ui.showMessage("Happy Hour is ON: 20% off orders taken this turn and next turn.");
            } catch (InvalidActionException e) {
                ui.showError(e.getMessage());
            }
        }
        if (happyHour.isActive()) {
            for (Customer c : restaurant.recoverWaitingCustomers(HappyHour.RECOVERY)) {
                ui.showMessage("Happy Hour: " + c + " recovers to satisfaction " + c.getSatisfaction() + ".");
            }
        }
    }

    /** The Host may seat one queued customer, in addition to the Waiter's task. */
    private void hostStep() {
        while (true) {
            int id = ui.readInt("Host: seat which queued customer? (number, 0 = wait): ", 0, 99);
            if (id == 0) {
                return;
            }
            try {
                Customer customer = restaurant.findCustomer(id);
                host.seatCustomer(customer, restaurant);
                ui.showMessage("Host seats " + customer + " at Table " + customer.getTable().getNumber() + ".");
                return;
            } catch (InvalidActionException e) {
                ui.showError(e.getMessage());
            }
        }
    }

    /** The Waiter does at most one task. An invalid choice is explained and asked again. */
    private void waiterStep() {
        while (true) {
            int task = ui.readInt("Waiter task (1 seat, 2 take order, 3 serve, 0 wait): ", 0, 3);
            if (task == 0) {
                return;
            }
            int id = ui.readInt("Which customer? (number, 0 = back): ", 0, 99);
            if (id == 0) {
                continue;
            }
            try {
                Customer customer = restaurant.findCustomer(id);
                if (task == 1) {
                    waiter.seatCustomer(customer, restaurant);
                    ui.showMessage("Waiter seats " + customer + " at Table " + customer.getTable().getNumber() + ".");
                } else if (task == 2) {
                    Order order = waiter.takeOrder(customer, restaurant.getMenu(),
                            restaurant.getHappyHour());
                    ui.showMessage(customer + " orders " + order.getItem().getName() + " for "
                            + Money.format(order.getPriceCents()) + ".");
                } else {
                    waiter.serve(customer, turn);
                    ui.showMessage("Waiter serves " + customer.getOrder().getItem().getName()
                            + " to " + customer + ". Order SERVED.");
                }
                return;
            } catch (InvalidActionException e) {
                ui.showError(e.getMessage());
            }
        }
    }

    /** The Chef advances at most one order by one preparation unit. */
    private void chefStep() {
        while (true) {
            int id = ui.readInt("Chef: prepare whose order? (number, 0 = wait): ", 0, 99);
            if (id == 0) {
                return;
            }
            try {
                Order order = restaurant.findCustomer(id).getOrder();
                chef.prepare(order);
                String ready = order.getStatus() == OrderStatus.READY ? " - READY" : "";
                ui.showMessage("Chef prepares C" + id + "'s " + order.getItem().getName() + " ("
                        + order.getProgress() + ")" + ready + ".");
                return;
            } catch (InvalidActionException e) {
                ui.showError(e.getMessage());
            }
        }
    }

    /** Step 6, after the last turn: victory only if all three targets are met. */
    private void showResult() {
        boolean paidOk = restaurant.getPaidCount() >= TARGET_PAID;
        boolean revenueOk = restaurant.getRevenueCents() >= TARGET_REVENUE_CENTS;
        boolean satisfactionOk = restaurant.getAverageSatisfaction() >= TARGET_SATISFACTION;
        String[] lines = {
            "Paid customers:       " + restaurant.getPaidCount() + " (need " + TARGET_PAID + ") "
                    + (paidOk ? "OK" : "MISSED"),
            "Revenue:              " + Money.format(restaurant.getRevenueCents()) + " (need "
                    + Money.format(TARGET_REVENUE_CENTS) + ") " + (revenueOk ? "OK" : "MISSED"),
            "Average satisfaction: " + ui.formatAverage(restaurant.getAverageSatisfaction()) + " (need "
                    + TARGET_SATISFACTION + ") " + (satisfactionOk ? "OK" : "MISSED"),
            "Turns used:           " + MAX_TURNS + "/" + MAX_TURNS
        };
        ui.showResult(paidOk && revenueOk && satisfactionOk, restaurant, lines);
    }
}

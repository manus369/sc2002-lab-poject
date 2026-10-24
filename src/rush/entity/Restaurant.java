package rush.entity;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Holds the tables, the waiting queue, the customers and the results so far. */
public class Restaurant {

    private final Menu menu = new Menu();
    private final HappyHour happyHour = new HappyHour();
    private final List<Table> tables = new ArrayList<>();
    private final List<Customer> waitingQueue = new ArrayList<>();
    private final List<Customer> readyToPay = new ArrayList<>(); // oldest first
    private final List<Customer> customers = new ArrayList<>();  // everyone who arrived

    private int revenueCents = 0;
    private int paidCount = 0;
    private int totalPaidSatisfaction = 0;
    private int leftCount = 0;

    public Restaurant(int numberOfTables) {
        for (int i = 1; i <= numberOfTables; i++) {
            tables.add(new Table(i));
        }
    }

    public void addCustomer(Customer customer) {
        customers.add(customer);
        waitingQueue.add(customer);
    }

    public Customer findCustomer(int id) throws InvalidActionException {
        for (Customer c : customers) {
            if (c.getId() == id) {
                return c;
            }
        }
        throw new InvalidActionException("There is no customer C" + id + ".");
    }

    public Table findFreeTable() {
        for (Table t : tables) {
            if (t.isFree()) {
                return t;
            }
        }
        return null;
    }

    /** Moves a queued customer to a free table. Used by the Waiter and the Host. */
    public void seatCustomer(Customer customer) throws InvalidActionException {
        if (customer.getStatus() != CustomerStatus.WAITING) {
            throw new InvalidActionException(customer + " is not in the waiting queue.");
        }
        Table table = findFreeTable();
        if (table == null) {
            throw new InvalidActionException("There is no free table.");
        }
        table.seat(customer);
        customer.setTable(table);
        customer.setStatus(CustomerStatus.SEATED);
        waitingQueue.remove(customer);
    }

    /** Customers served on an earlier turn finish eating. Returns those who are now READY_TO_PAY. */
    public List<Customer> finishEating(int turn) {
        List<Customer> finished = new ArrayList<>();
        for (Customer c : customers) {
            if (c.getStatus() == CustomerStatus.SERVED && c.getServedTurn() < turn) {
                c.setStatus(CustomerStatus.READY_TO_PAY);
                readyToPay.add(c);
                finished.add(c);
            }
        }
        return finished;
    }

    public Customer nextToPay() {
        if (readyToPay.isEmpty()) {
            return null;
        }
        return readyToPay.get(0);
    }

    public void recordPayment(Customer customer) {
        readyToPay.remove(customer);
        revenueCents += customer.getOrder().getPriceCents();
        paidCount++;
        totalPaidSatisfaction += customer.getSatisfaction();
        Table table = customer.getTable();
        table.free();
        customer.setTable(null);
    }

    /** Happy Hour: customers waiting for a table or for food recover some satisfaction. */
    public List<Customer> recoverWaitingCustomers(int amount) {
        List<Customer> recovered = new ArrayList<>();
        for (Customer c : customers) {
            if (c.isWaitingForService()) {
                c.recover(amount);
                recovered.add(c);
            }
        }
        return recovered;
    }

    /** End of turn: unserved customers lose satisfaction. Returns those who reached 0 and left. */
    public List<Customer> applyWaitingLoss() {
        List<Customer> left = new ArrayList<>();
        for (Customer c : customers) {
            if (c.isWaitingForService()) {
                c.loseSatisfaction();
                if (c.getSatisfaction() == 0) {
                    removeUnhappyCustomer(c);
                    left.add(c);
                }
            }
        }
        return left;
    }

    private void removeUnhappyCustomer(Customer c) {
        c.setStatus(CustomerStatus.LEFT);
        if (c.getOrder() != null) {
            c.getOrder().cancel();
        }
        if (c.getTable() != null) {
            c.getTable().free();
            c.setTable(null);
        }
        waitingQueue.remove(c);
        leftCount++;
    }

    public List<Customer> getCustomersWithStatus(CustomerStatus status) {
        List<Customer> result = new ArrayList<>();
        for (Customer c : customers) {
            if (c.getStatus() == status) {
                result.add(c);
            }
        }
        return result;
    }

    /** Customers whose order is READY and can be served by the Waiter. */
    public List<Customer> getCustomersReadyToServe() {
        List<Customer> result = new ArrayList<>();
        for (Customer c : customers) {
            if (c.getOrder() != null && c.getOrder().getStatus() == OrderStatus.READY) {
                result.add(c);
            }
        }
        return result;
    }

    /** Orders that the Chef can still work on. */
    public List<Order> getOrdersInKitchen() {
        List<Order> result = new ArrayList<>();
        for (Customer c : customers) {
            if (c.getOrder() != null && c.getOrder().getStatus() == OrderStatus.PREPARING) {
                result.add(c.getOrder());
            }
        }
        return result;
    }

    /** Average satisfaction of paid customers, or -1 if nobody has paid yet. */
    public double getAverageSatisfaction() {
        if (paidCount == 0) {
            return -1;
        }
        return (double) totalPaidSatisfaction / paidCount;
    }

    public Menu getMenu() {
        return menu;
    }

    public HappyHour getHappyHour() {
        return happyHour;
    }

    public List<Table> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public List<Customer> getWaitingQueue() {
        return Collections.unmodifiableList(waitingQueue);
    }

    public int getRevenueCents() {
        return revenueCents;
    }

    public int getPaidCount() {
        return paidCount;
    }

    /** Customers who have been served their food (including those who have paid since). */
    public int getServedCount() {
        int count = 0;
        for (Customer c : customers) {
            CustomerStatus s = c.getStatus();
            if (s == CustomerStatus.SERVED || s == CustomerStatus.READY_TO_PAY || s == CustomerStatus.PAID) {
                count++;
            }
        }
        return count;
    }

    public int getLeftCount() {
        return leftCount;
    }
}

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HotelReservationApp.java
 * Single-file console hotel reservation system using OOP and file I/O (serialization).
 *
 * Save as: HotelReservationApp.java
 * Compile: javac HotelReservationApp.java
 * Run: java HotelReservationApp
 */
public class HotelReservationApp {

    // ---------- Entry point ----------
    public static void main(String[] args) {
        ReservationManager manager = new ReservationManager();
        manager.loadState();               // load rooms/reservations from disk (or create sample data)
        ConsoleUI ui = new ConsoleUI(manager);
        ui.run();
    }

    // ---------- Enums ----------
    enum Category {
        STANDARD, DELUXE, SUITE;

        public static Category fromString(String s) {
            s = s.trim().toUpperCase();
            switch (s) {
                case "STANDARD": return STANDARD;
                case "DELUXE": return DELUXE;
                case "SUITE": return SUITE;
                default: throw new IllegalArgumentException("Unknown category: " + s);
            }
        }
    }

    enum ReservationStatus {
        CONFIRMED, CANCELLED, FAILED_PAYMENT
    }

    // ---------- Models ----------
    static class Room implements Serializable {
        private static final long serialVersionUID = 1L;
        int id;
        Category category;
        double pricePerNight;

        public Room(int id, Category category, double pricePerNight) {
            this.id = id;
            this.category = category;
            this.pricePerNight = pricePerNight;
        }

        @Override
        public String toString() {
            return String.format("Room[%d] %s - %.2f per night", id, category, pricePerNight);
        }
    }

    static class Reservation implements Serializable {
        private static final long serialVersionUID = 1L;
        String reservationId;
        int roomId;
        String guestName;
        LocalDate checkIn;
        LocalDate checkOut;
        double amount;
        ReservationStatus status;

        public Reservation(String reservationId, int roomId, String guestName,
                           LocalDate checkIn, LocalDate checkOut, double amount, ReservationStatus status) {
            this.reservationId = reservationId;
            this.roomId = roomId;
            this.guestName = guestName;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.amount = amount;
            this.status = status;
        }

        @Override
        public String toString() {
            return String.format("Reservation[%s] Guest: %s, Room: %d, %s -> %s, Amount: %.2f, Status: %s",
                    reservationId, guestName, roomId, checkIn, checkOut, amount, status);
        }
    }

    // ---------- Manager (business logic + persistence) ----------
    static class ReservationManager {
        private final String ROOMS_FILE = "rooms.dat";
        private final String RES_FILE = "reservations.dat";

        List<Room> rooms = new ArrayList<>();
        List<Reservation> reservations = new ArrayList<>();
        Random random = new Random();

        // Load rooms & reservations from files. If rooms file not found, create sample rooms.
        @SuppressWarnings("unchecked")
        public void loadState() {
            // load rooms
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ROOMS_FILE))) {
                rooms = (List<Room>) ois.readObject();
                System.out.println("Loaded rooms from " + ROOMS_FILE);
            } catch (Exception e) {
                System.out.println("Rooms file not found or failed to load. Creating sample rooms...");
                createSampleRooms();
                saveRooms();
            }

            // load reservations
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(RES_FILE))) {
                reservations = (List<Reservation>) ois.readObject();
                System.out.println("Loaded reservations from " + RES_FILE);
            } catch (Exception e) {
                System.out.println("No existing reservations found or failed to load.");
                saveReservations(); // create empty file for first run
            }
        }

        private void createSampleRooms() {
            rooms.clear();
            rooms.add(new Room(101, Category.STANDARD, 40.0));
            rooms.add(new Room(102, Category.STANDARD, 45.0));
            rooms.add(new Room(201, Category.DELUXE, 80.0));
            rooms.add(new Room(202, Category.DELUXE, 85.0));
            rooms.add(new Room(301, Category.SUITE, 150.0));
        }

        public void saveRooms() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ROOMS_FILE))) {
                oos.writeObject(rooms);
            } catch (IOException e) {
                System.err.println("Failed to save rooms: " + e.getMessage());
            }
        }

        public void saveReservations() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(RES_FILE))) {
                oos.writeObject(reservations);
            } catch (IOException e) {
                System.err.println("Failed to save reservations: " + e.getMessage());
            }
        }

        // Search available rooms by category and date range
        public List<Room> searchAvailableRooms(Category category, LocalDate from, LocalDate to) {
            List<Room> candidates = new ArrayList<>();
            for (Room r : rooms) {
                if (r.category != category) continue;
                if (isRoomAvailable(r.id, from, to)) {
                    candidates.add(r);
                }
            }
            return candidates;
        }

        // Check availability by ensuring no overlapping confirmed reservations
        public boolean isRoomAvailable(int roomId, LocalDate from, LocalDate to) {
            for (Reservation res : reservations) {
                if (res.roomId != roomId) continue;
                if (res.status != ReservationStatus.CONFIRMED) continue;
                // overlap condition: (res.checkIn < to) && (from < res.checkOut)
                if ((res.checkIn.isBefore(to)) && (from.isBefore(res.checkOut))) {
                    return false;
                }
            }
            return true;
        }

        // Make a reservation: returns Reservation if success else null
        public Reservation makeReservation(int roomId, String guestName, LocalDate checkIn, LocalDate checkOut) {
            // basic validations
            if (!checkIn.isBefore(checkOut) && !checkIn.equals(checkOut)) {
                System.out.println("Invalid dates: check-in must be before check-out.");
                return null;
            }
            if (!isRoomAvailable(roomId, checkIn, checkOut)) {
                System.out.println("Selected room is not available for given dates.");
                return null;
            }
            Optional<Room> optRoom = rooms.stream().filter(r -> r.id == roomId).findFirst();
            if (!optRoom.isPresent()) {
                System.out.println("Room not found.");
                return null;
            }
            Room room = optRoom.get();
            long nights = checkOut.toEpochDay() - checkIn.toEpochDay();
            if (nights <= 0) nights = 1; // fallback
            double amount = nights * room.pricePerNight;

            // Simulate payment
            boolean paymentOK = PaymentSimulator.processPayment(amount);
            String resId = generateReservationId();
            Reservation res;
            if (paymentOK) {
                res = new Reservation(resId, roomId, guestName, checkIn, checkOut, amount, ReservationStatus.CONFIRMED);
                reservations.add(res);
                saveReservations();
                System.out.println("Payment succeeded. Reservation confirmed: " + resId);
            } else {
                res = new Reservation(resId, roomId, guestName, checkIn, checkOut, amount, ReservationStatus.FAILED_PAYMENT);
                reservations.add(res);
                saveReservations();
                System.out.println("Payment failed. Reservation recorded as FAILED_PAYMENT with id: " + resId);
            }
            return res;
        }

        // Cancel reservation by reservationId
        public boolean cancelReservation(String reservationId) {
            for (Reservation r : reservations) {
                if (r.reservationId.equals(reservationId)) {
                    if (r.status == ReservationStatus.CANCELLED) {
                        System.out.println("Reservation already cancelled.");
                        return false;
                    }
                    r.status = ReservationStatus.CANCELLED;
                    saveReservations();
                    System.out.println("Reservation " + reservationId + " cancelled.");
                    return true;
                }
            }
            System.out.println("Reservation id not found.");
            return false;
        }

        public List<Reservation> getReservationsForGuest(String guestName) {
            List<Reservation> res = new ArrayList<>();
            for (Reservation r : reservations) {
                if (r.guestName.equalsIgnoreCase(guestName)) res.add(r);
            }
            return res;
        }

        public Reservation getReservationById(String id) {
            for (Reservation r : reservations) if (r.reservationId.equals(id)) return r;
            return null;
        }

        private String generateReservationId() {
            return "R" + (1000 + random.nextInt(9000));
        }
    }

    // ---------- Payment simulator ----------
    static class PaymentSimulator {
        // Simulate payment: 85% chance succeed
        public static boolean processPayment(double amount) {
            System.out.printf("Processing payment of %.2f ...\n", amount);
            try {
                Thread.sleep(400); // small delay to simulate processing (safe short)
            } catch (InterruptedException ignored) {}
            boolean ok = Math.random() < 0.85;
            System.out.println(ok ? "Payment approved." : "Payment declined.");
            return ok;
        }
    }

    // ---------- Console UI ----------
    static class ConsoleUI {
        private final ReservationManager manager;
        private final Scanner sc = new Scanner(System.in);
        private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        public ConsoleUI(ReservationManager manager) {
            this.manager = manager;
        }

        public void run() {
            printHeader();
            while (true) {
                printMenu();
                String choice = sc.nextLine().trim();
                switch (choice) {
                    case "1": handleSearchAndBook(); break;
                    case "2": handleCancel(); break;
                    case "3": handleViewBookingsByGuest(); break;
                    case "4": handleViewReservationDetails(); break;
                    case "5": showAllRooms(); break;
                    case "0": exitApp(); return;
                    default: System.out.println("Unknown option. Try again."); break;
                }
            }
        }

        private void printHeader() {
            System.out.println("=== Hotel Reservation System ===");
        }

        private void printMenu() {
            System.out.println("\nMenu:");
            System.out.println("1) Search rooms & Book");
            System.out.println("2) Cancel reservation");
            System.out.println("3) View my bookings (by guest name)");
            System.out.println("4) View booking details (by reservation id)");
            System.out.println("5) List all rooms");
            System.out.println("0) Exit");
            System.out.print("Choose: ");
        }

        private void handleSearchAndBook() {
            try {
                System.out.print("Enter category (Standard/Deluxe/Suite): ");
                Category cat = Category.fromString(sc.nextLine());
                System.out.print("Check-in date (YYYY-MM-DD): ");
                LocalDate in = LocalDate.parse(sc.nextLine().trim(), dtf);
                System.out.print("Check-out date (YYYY-MM-DD): ");
                LocalDate out = LocalDate.parse(sc.nextLine().trim(), dtf);

                List<Room> avail = manager.searchAvailableRooms(cat, in, out);
                if (avail.isEmpty()) {
                    System.out.println("No available rooms found for those dates.");
                    return;
                }
                System.out.println("Available rooms:");
                for (Room r : avail) System.out.println(" - " + r);

                System.out.print("Enter room id to book: ");
                int roomId = Integer.parseInt(sc.nextLine().trim());
                System.out.print("Your full name: ");
                String guest = sc.nextLine().trim();

                Reservation res = manager.makeReservation(roomId, guest, in, out);
                if (res != null) {
                    System.out.println("Reservation result: " + res);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        private void handleCancel() {
            System.out.print("Enter reservation id to cancel (e.g. R1234): ");
            String id = sc.nextLine().trim();
            manager.cancelReservation(id);
        }

        private void handleViewBookingsByGuest() {
            System.out.print("Enter guest name: ");
            String name = sc.nextLine().trim();
            List<Reservation> list = manager.getReservationsForGuest(name);
            if (list.isEmpty()) System.out.println("No reservations found for " + name);
            else {
                System.out.println("Reservations:");
                list.forEach(r -> System.out.println(" - " + r));
            }
        }

        private void handleViewReservationDetails() {
            System.out.print("Enter reservation id: ");
            String id = sc.nextLine().trim();
            Reservation r = manager.getReservationById(id);
            if (r == null) System.out.println("Reservation not found.");
            else System.out.println(r);
        }

        private void showAllRooms() {
            System.out.println("Rooms in system:");
            manager.rooms.forEach(r -> System.out.println(" - " + r));
        }

        private void exitApp() {
            System.out.println("Saving data and exiting. Goodbye!");
            manager.saveRooms();
            manager.saveReservations();
        }
    }
}

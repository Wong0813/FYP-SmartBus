# Security Specification for SmartBus UPSI

## Data Invariants
1. A Student can only read user profiles (limited) and real-time bus data.
2. A Driver can update their own bus data (GPS, speed, next stop).
3. An Admin can create and manage user accounts and bus lists.
4. Only verified email users should be allowed to perform writes (except for initial user creation by admin).
5. Bus location updates must be throttled or at least verified to be from the assigned driver.

## The Dirty Dozen (Payloads to Block)
1. **The Identity Thief**: A student trying to update another user's role to 'admin'.
2. **The Ghost Driver**: An unauthenticated user sending GPS coordinates to a bus.
3. **The Speed Demon**: A driver setting an impossible speed (e.g., 500 km/h).
4. **The Route Hijacker**: A student trying to delete a bus route.
5. **The Shadow Account**: A user trying to create their own account with 'admin' role directly in Firestore.
6. **The Location Spammer**: A driver sending massive strings (1MB) as location coordinates.
7. **The Status Manipulator**: A student trying to set a bus status to 'inactive' to mess with other students.
8. **The PII Scraper**: A student trying to read all driver's private emails.
9. **The Orphaned Bus**: Creating a bus without a valid name or driver ID.
10. **The Time Traveler**: Setting a `lastUpdate` to a future date.
11. **The ID Poisoner**: Sending a 1KB string as a `busId`.
12. **The Relationship Breaker**: A driver trying to update a bus they aren't assigned to.

## Proposed Rules Structure
- Global deny.
- `isValidUser`: Checks shape of user doc.
- `isValidBus`: Checks GPS format and speed bounds.
- `isOwner(uid)`: `request.auth.uid == uid`.
- `isAdmin()`: Check if `get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin'`.
- `isDriver()`: Check if `get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'driver'`.

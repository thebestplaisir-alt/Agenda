import SwiftUI

struct AgendaPadelApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var selectedDate = Date()
    @State private var currentMonth = Date()

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.97, green: 0.98, blue: 0.98).ignoresSafeArea()

                VStack(spacing: 0) {
                    // Custom Calendar View
                    MonthCalendarView(selectedDate: $selectedDate, currentMonth: $currentMonth)
                        .padding()

                    // Search Bar
                    HStack {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.gray)
                            Text("Rechercher des joueurs...")
                                .foregroundColor(.gray)
                                .font(.system(size: 14))
                            Spacer()
                        }
                        .padding(10)
                        .background(Color.white)
                        .cornerRadius(12)

                        Button(action: {}) {
                            Image(systemName: "plus")
                                .frame(width: 56, height: 56)
                                .background(Color(red: 0, green: 0.38, blue: 0.64))
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)

                    // List of sessions (Empty state placeholder)
                    ScrollView {
                        VStack(spacing: 16) {
                            Image(systemName: "calendar.badge.exclamationmark")
                                .font(.system(size: 64))
                                .foregroundColor(Color(red: 0.9, green: 0.9, blue: 0.9))
                                .padding(.top, 40)
                            Text("Aucune session trouvée")
                                .foregroundColor(.gray)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack {
                        Text("AGENDA PADEL")
                            .font(.system(size: 14, weight: .black))
                        Text("0 matches / sessions")
                            .font(.system(size: 10))
                            .opacity(0.7)
                    }
                    .foregroundColor(.white)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack {
                        Image(systemName: "bell")
                        Image(systemName: "chart.bar")
                        Image(systemName: "person.2")
                        Image(systemName: "questionmark.circle")
                        Circle()
                            .frame(width: 28, height: 28)
                            .overlay(Image(systemName: "person.fill").foregroundColor(.white))
                    }
                    .foregroundColor(.white)
                }
            }
            .toolbarBackground(Color(red: 0.1, green: 0.11, blue: 0.12), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

struct MonthCalendarView: View {
    @Binding var selectedDate: Date
    @Binding var currentMonth: Date

    let days = ["L", "M", "M", "J", "V", "S", "D"]

    var body: some View {
        VStack {
            // Month Header
            HStack {
                Button(action: {}) { Image(systemName: "chevron.left") }
                Spacer()
                Text("Mai 2024")
                    .fontWeight(.bold)
                Spacer()
                Button(action: {}) { Image(systemName: "chevron.right") }
            }
            .padding(.bottom, 12)

            // Days Header
            HStack {
                ForEach(days, id: \.self) { day in
                    Text(day)
                        .frame(maxWidth: .infinity)
                        .font(.system(size: 12))
                        .foregroundColor(.lightGray)
                }
            }

            // Calendar Grid (Static Sample)
            VStack(spacing: 8) {
                ForEach(0..<5) { row in
                    HStack(spacing: 8) {
                        ForEach(0..<7) { col in
                            DayCell(number: row * 7 + col, isSelected: (row * 7 + col) == 24)
                        }
                    }
                }
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.05), radius: 2)
    }
}

struct DayCell: View {
    let number: Int
    let isSelected: Bool

    var body: some View {
        VStack {
            Text("\(number)")
                .font(.system(size: 13, weight: isSelected ? .bold : .semibold))
                .foregroundColor(isSelected ? .white : .black)
            Spacer().frame(height: 4)
            Circle()
                .frame(width: 10, height: 10)
                .foregroundColor(isSelected ? .white : .red)
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(1, contentMode: .fill)
        .background(isSelected ? Color(red: 0.1, green: 0.11, blue: 0.12) : Color(red: 0.93, green: 0.27, blue: 0.27).opacity(0.12))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color(red: 0.93, green: 0.27, blue: 0.27).opacity(0.4), lineWidth: isSelected ? 0 : 1)
        )
    }
}

extension Color {
    static let lightGray = Color(red: 0.8, green: 0.8, blue: 0.8)
}

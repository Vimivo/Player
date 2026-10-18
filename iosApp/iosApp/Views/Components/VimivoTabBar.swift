import SwiftUI
import UIKit

/// Geometry shared by the floating tab bar and the now-playing bar so they read as one stack.
/// Mirrors `VimivoShell.floatingChrome*` and `tabBarHeight` in the shared Compose design system.
enum VimivoChrome {
    static let margin: CGFloat = 16
    static let radius: CGFloat = 32
    static let gap: CGFloat = 8
    static let tabBarHeight: CGFloat = 64
}

public struct VimivoTab: Identifiable, Hashable {
    public let id: Int
    public let title: String
    public let icon: String
    public let selectedIcon: String

    public static let primary: [VimivoTab] = [
        VimivoTab(id: 0, title: "Home", icon: "house", selectedIcon: "house.fill"),
        VimivoTab(id: 1, title: "Music", icon: "music.note", selectedIcon: "music.note"),
        VimivoTab(id: 2, title: "Videos", icon: "film", selectedIcon: "film.fill"),
        VimivoTab(id: 3, title: "Playlists", icon: "music.note.list", selectedIcon: "music.note.list"),
        VimivoTab(id: 4, title: "Settings", icon: "gearshape", selectedIcon: "gearshape.fill"),
    ]
}

extension View {
    /// Floating glass capsule: a system material blurs the content scrolling behind it, and becomes opaque when
    /// Reduce Transparency is on.
    func vimivoFloatingChrome() -> some View {
        let shape = RoundedRectangle(cornerRadius: VimivoChrome.radius, style: .continuous)
        return background(.ultraThinMaterial, in: shape)
            .clipShape(shape)
            .overlay(shape.strokeBorder(Color.primary.opacity(0.08), lineWidth: 1))
            .shadow(color: Color.black.opacity(0.12), radius: 10, x: 0, y: 4)
    }
}

/// Floating capsule navigation with a spring-animated pill behind the selected icon and label.
public struct VimivoTabBar: View {
    public let tabs: [VimivoTab]
    @Binding public var selection: Int
    @Namespace private var pillNamespace
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(tabs: [VimivoTab] = VimivoTab.primary, selection: Binding<Int>) {
        self.tabs = tabs
        self._selection = selection
    }

    public var body: some View {
        HStack(spacing: 0) {
            ForEach(tabs) { tab in
                tabButton(tab)
            }
        }
        .padding(4)
        .frame(height: VimivoChrome.tabBarHeight)
        .vimivoFloatingChrome()
        .padding(.horizontal, VimivoChrome.margin)
        .padding(.bottom, VimivoChrome.gap)
    }

    private func tabButton(_ tab: VimivoTab) -> some View {
        let selected = tab.id == selection
        return Button(action: { select(tab.id) }) {
            VStack(spacing: 2) {
                Image(systemName: selected ? tab.selectedIcon : tab.icon)
                    .font(.system(size: 19, weight: selected ? .semibold : .regular))
                    .frame(height: 24)
                Text(tab.title)
                    .font(.system(size: 12, weight: selected ? .semibold : .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .foregroundStyle(selected ? Color.accentColor : Color.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                if selected {
                    Capsule()
                        .fill(Color.accentColor.opacity(0.18))
                        .matchedGeometryEffect(id: "pill", in: pillNamespace)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(UnhighlightedButtonStyle())
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private func select(_ id: Int) {
        guard id != selection else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        withAnimation(reduceMotion ? nil : .spring(response: 0.38, dampingFraction: 0.75)) {
            selection = id
        }
    }
}

/// Removes the pressed-state dimming so selection feedback comes only from the pill and haptic.
private struct UnhighlightedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}

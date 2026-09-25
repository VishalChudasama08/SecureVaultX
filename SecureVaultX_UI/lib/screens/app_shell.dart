import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/breakpoints.dart';
import '../routing/app_router.dart';
import '../state/auth_controller.dart';

class _Destination {
  const _Destination(this.route, this.label, this.icon, this.selectedIcon);

  final String route;
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

const _destinations = [
  _Destination(Routes.home, 'Home', Icons.home_outlined, Icons.home),
  _Destination(Routes.encrypt, 'Encrypt', Icons.lock_outline, Icons.lock),
  _Destination(Routes.vault, 'Vault', Icons.folder_outlined, Icons.folder),
  _Destination(Routes.decrypt, 'Decrypt', Icons.lock_open_outlined, Icons.lock_open),
];

/// Adaptive navigation: bottom bar on phones, rail on tablets, extended rail on wide desktop windows.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.location, required this.child});

  final String location;
  final Widget child;

  int get _index {
    final i = _destinations.indexWhere((d) => d.route == location);
    return i < 0 ? 0 : i;
  }

  void _go(BuildContext context, int i) => context.go(_destinations[i].route);

  @override
  Widget build(BuildContext context) {
    final compact = Breakpoints.isCompact(context);
    final expanded = Breakpoints.isExpanded(context);
    final auth = context.watch<AuthController>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('SecureVaultX'),
        actions: [
          if (!compact && auth.user != null)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Center(child: Text(auth.user!.email, style: Theme.of(context).textTheme.bodyMedium)),
            ),
          IconButton(
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthController>().logout(),
          ),
        ],
      ),
      body: SafeArea(
        child: Row(children: [
          if (!compact)
            NavigationRail(
              selectedIndex: _index,
              extended: expanded,
              labelType: expanded ? NavigationRailLabelType.none : NavigationRailLabelType.all,
              onDestinationSelected: (i) => _go(context, i),
              destinations: [
                for (final d in _destinations)
                  NavigationRailDestination(
                    icon: Icon(d.icon),
                    selectedIcon: Icon(d.selectedIcon),
                    label: Text(d.label),
                  ),
              ],
            ),
          Expanded(child: child),
        ]),
      ),
      bottomNavigationBar: compact
          ? NavigationBar(
              selectedIndex: _index,
              onDestinationSelected: (i) => _go(context, i),
              destinations: [
                for (final d in _destinations)
                  NavigationDestination(icon: Icon(d.icon), selectedIcon: Icon(d.selectedIcon), label: d.label),
              ],
            )
          : null,
    );
  }
}

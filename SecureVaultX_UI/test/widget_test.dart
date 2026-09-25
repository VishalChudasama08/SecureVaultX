import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:securevaultx_ui/app.dart';
import 'package:securevaultx_ui/models/vault_file.dart';
import 'package:securevaultx_ui/services/local_file_service.dart';
import 'package:securevaultx_ui/state/auth_controller.dart';

import 'fakes.dart';

Future<AuthController> pumpApp(WidgetTester tester, FakeAuthGateway gateway, {FakeVaultGateway? vault}) async {
  final auth = AuthController(gateway);
  await tester.pumpWidget(SecureVaultXApp(auth: auth, vault: vault ?? FakeVaultGateway(), files: LocalFileService()));
  await tester.pumpAndSettle();
  return auth;
}

void main() {
  testWidgets('signed-out users land on the login screen', (tester) async {
    await pumpApp(tester, FakeAuthGateway());
    expect(find.text('Sign in to your encrypted vault'), findsOneWidget);
    expect(find.text('Welcome, Test User'), findsNothing);
  });

  testWidgets('a persisted session skips the login screen', (tester) async {
    await pumpApp(tester, FakeAuthGateway(sessionUser: testUser()));
    expect(find.text('Welcome, Test User'), findsOneWidget);
  });

  testWidgets('empty form is rejected locally, wrong password shows a friendly error', (tester) async {
    await pumpApp(tester, FakeAuthGateway());
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pump();
    expect(find.text('Email is required'), findsOneWidget);
    expect(find.text('Password is required'), findsOneWidget);

    await tester.enterText(find.byType(TextFormField).at(0), 'test@example.com');
    await tester.enterText(find.byType(TextFormField).at(1), 'nope');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    expect(find.text('Incorrect email or password.'), findsOneWidget);
  });

  testWidgets('signing in shows home; signing out returns to login', (tester) async {
    final gateway = FakeAuthGateway();
    await pumpApp(tester, gateway);
    await tester.enterText(find.byType(TextFormField).at(0), 'test@example.com');
    await tester.enterText(find.byType(TextFormField).at(1), 'password123');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    expect(find.text('Welcome, Test User'), findsOneWidget);

    await tester.tap(find.byTooltip('Sign out'));
    await tester.pumpAndSettle();
    expect(find.text('Sign in to your encrypted vault'), findsOneWidget);
    expect(gateway.logoutCalls, 1);
  });

  testWidgets('registration validates locally before calling the server', (tester) async {
    await pumpApp(tester, FakeAuthGateway());
    await tester.tap(find.text('Create an account'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'New User');
    await tester.enterText(find.byType(TextFormField).at(1), 'not-an-email');
    await tester.enterText(find.byType(TextFormField).at(2), 'short');
    await tester.enterText(find.byType(TextFormField).at(3), 'different');
    await tester.tap(find.widgetWithText(FilledButton, 'Create account'));
    await tester.pump();
    expect(find.text('Enter a valid email address'), findsOneWidget);
    expect(find.text('Use at least 8 characters'), findsOneWidget);
    expect(find.text('Passwords do not match'), findsOneWidget);
  });

  testWidgets('vault lists files and shows the empty state', (tester) async {
    final vault = FakeVaultGateway(files: [
      VaultFile(id: '1', filename: 'notes.txt', size: 2048, createdAt: DateTime.utc(2026, 9, 1, 10, 30)),
    ]);
    await pumpApp(tester, FakeAuthGateway(sessionUser: testUser()), vault: vault);
    await tester.tap(find.text('Vault').first);
    await tester.pumpAndSettle();
    expect(find.text('notes.txt'), findsOneWidget);
    expect(find.textContaining('2 KB'), findsOneWidget);

    vault.files.clear();
    await tester.tap(find.byTooltip('Refresh'));
    await tester.pumpAndSettle();
    expect(find.text('Your vault is empty'), findsOneWidget);
  });

  testWidgets('layout adapts: bottom bar on phones, rail on wide windows', (tester) async {
    tester.view.physicalSize = const Size(400, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    await pumpApp(tester, FakeAuthGateway(sessionUser: testUser()));
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);

    tester.view.physicalSize = const Size(1400, 900);
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
  });
}

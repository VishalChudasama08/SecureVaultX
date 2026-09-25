class AppUser {
  const AppUser({required this.fullName, required this.email, required this.createdAt});

  final String fullName;
  final String email;
  final DateTime createdAt;

  factory AppUser.fromJson(Map<String, dynamic> json) => AppUser(
        fullName: json['fullName'] as String,
        email: json['email'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

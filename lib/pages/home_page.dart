import 'package:flutter/material.dart';


class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}


class _HomePageState extends State<HomePage> {
  @override
  void initState() {
    super.initState();
    // init here
  }

  @override
  void dispose() {
    // dispose here
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('Home'));
  }
}
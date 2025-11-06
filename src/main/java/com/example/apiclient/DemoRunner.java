package com.example.apiclient;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoRunner implements CommandLineRunner {
	private final ApiConsole console;

	public DemoRunner(ApiConsole console) { this.console = console; }

	public static void main(String[] args) { SpringApplication.run(DemoRunner.class, args); }


	@Override
	public void run(String... args) {
		try {
			console.runInteractive();
		} catch (Exception e) {
			e.printStackTrace();      // show the real cause
		}
	}

}

package br.fiap.daiji.assistant;

@FunctionalInterface
public interface NaturalLanguageResponder {
    AssistantResponse answer(String text);
}

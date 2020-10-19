/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc.
 ******************************************************************************/
package com.redhat.devtools.recognizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;


import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class LanguageRecognizer {

    public static void main(String[] args) {
        if (args.length == 0) return;

        Map<Language, Integer> languagesDetected = new HashMap<>();
        // init dictionary languages
        LanguageHandler handler = LanguageHandler.get();

        // get directory and extract all files
        Path root = Paths.get(args[0]);
        List<String> files = Collections.emptyList();
        try {
            files = getFiles(root);
        } catch (IOException e) {}


        // save all extensions extracted from files + their occurrences
        Map<String, Long> extensions = files.stream().collect(groupingBy(file -> "." + FilenameUtils.getExtension(file), counting()));

        // get languages belonging to extensions found
        extensions.keySet().stream().forEach(extension -> {
            List<Language> languages = handler.getLanguagesByExtension(extension);
            if (languages.isEmpty()) return;
            languages.stream().forEach(language -> {
                Language tmpLanguage = language.getGroup().isEmpty() ? language : handler.getLanguageByName(language.getGroup());
                long percentage = languagesDetected.getOrDefault(tmpLanguage, 0) + extensions.get(extension);
                languagesDetected.put(tmpLanguage, (int) percentage);
            });
        });

        // get only programming language and calculate percentage
        int totalProgrammingOccurences = (int) languagesDetected.keySet().stream().
                filter(lang -> lang.getType().equalsIgnoreCase("programming")).
                mapToLong(lang -> languagesDetected.get(lang)).sum();

        // only keep programming language which consists of atleast the 2% of the project
        Map<Language, Double> programmingLanguagesDetected = languagesDetected.keySet().stream().
                filter(lang -> lang.getType().equalsIgnoreCase("programming")).
                filter(lang -> (double)languagesDetected.get(lang) / totalProgrammingOccurences > 0.02).
                collect(Collectors.toMap(lang -> lang, lang -> (double)languagesDetected.get(lang) / totalProgrammingOccurences * 100));

        // if java is a programming language, find more infos
        Optional<Language> java = programmingLanguagesDetected.keySet().stream().filter(lang -> lang.getName().equalsIgnoreCase("java")).findFirst();
        if (java.isPresent()) {
            String javaLanguage = "";
            try {
                javaLanguage = JavaLanguageRecognizer.getJava(files);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            }
            Language javaLang = java.get();
            double percJava = programmingLanguagesDetected.remove(javaLang);
            javaLang.setName(javaLanguage);
            programmingLanguagesDetected.put(javaLang, percJava);
        }

        AtomicReference<String> t = new AtomicReference<>("");
        programmingLanguagesDetected.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEachOrdered(e -> {
            t.updateAndGet(v1 -> v1 + e.getKey().getName() + String.format(" % .2f", e.getValue()) + "%\n");
        });

        // if multiple languages are found, get content from files and check their syntax

        // print language
        System.out.println(t.get());
    }


    private static List<String> getFiles(Path rootDirectory) throws IOException {
        return Files.walk(rootDirectory, Integer.MAX_VALUE).filter(Files::isRegularFile).map(String::valueOf)
                .collect(Collectors.toList());

    }


}

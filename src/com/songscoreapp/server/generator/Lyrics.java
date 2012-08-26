package com.songscoreapp.server.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.googlecode.objectify.Objectify;
import com.songscoreapp.server.objectify.RhymingDictionary;
import com.songscoreapp.server.twitter.TwitterUtil;

public class Lyrics {

    RhymingDictionary dictionary;

    public Lyrics(RhymingDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public static List<List<String>> getLyrics(String goodLine) {

        List<String> line = new ArrayList<String>();
        for(String word : goodLine.split(" ")) {
            line.add(word);
        }

        int syllablesInLine = SyllableUtil.getSyllableCountFromLine(line);

        String[] laWords = new String[] {"blah", "cha", "da", "fah", "ga", "la", "na", "sha", "tra", "what"};
        String laWord = laWords[(int) (Math.random() * laWords.length)];
        List<String> laLine = new ArrayList<String>();
        for(int i = 0; i < syllablesInLine; i++) {
            laLine.add(laWord);
        }

        List<List<String>> lyrics = new ArrayList<List<String>>();
        lyrics.add(laLine);
        lyrics.add(laLine);
        lyrics.add(laLine);
        lyrics.add(line);
        return lyrics;
    }

    /**
     * Finds the most significant word from a phrase.
     *
     * Rules:
     * look for words surrounded by asterisks
     * look for what looks like a last name
     * look at the longest capitalized word (unless its the very first word or like "I")
     * look for the longest word
     * @param phrase
     * @return
     */
    static private int LAST_NAME = 2000;
    static private int CAPITALIZED = 1000;
    public static String getSignificantWord(String phrase) {
        String[] words = phrase.split(" ");
        String bestWord = "";
        int highestSignificance = 0;
        boolean previousWordCapitalized = false;
        boolean firstWord = true;
        for(String word : words) {
            String trimmedWord = trimPunctuation(word);
            if(word.startsWith("*") && word.endsWith("*")) {
                // This is the word we want. No need to dally.
                return trimmedWord;
            }

            int significance = trimmedWord.length();
            if(isCapitalized(trimmedWord)) {
                if(previousWordCapitalized) {
                   significance += LAST_NAME;
                } else if(!firstWord) {
                    significance += CAPITALIZED;
                }
                previousWordCapitalized = true;
            } else {
                previousWordCapitalized = false;
            }

            if(significance > highestSignificance) {
                bestWord = word;
                highestSignificance = significance;
            }

            firstWord = false;
        }
        return bestWord;
    }

    public static List<String> trimPhrasePunctuation(List<String> phrases) {
        List<String> trimmedPhrases = new ArrayList<String>();
        for(String phrase : phrases) {
            trimmedPhrases.add(trimPhrasePunctuation(phrase));
        }
        return trimmedPhrases;
    }

    public static String trimPhrasePunctuation(String phrase) {
        StringBuffer newPhrase = new StringBuffer("");
        String[] words = phrase.split("\\s+");
        for(String word : words) {
            String newWord = trimPunctuation(word);
            if(newWord != null && newWord.length() > 0) {
                newPhrase.append(" " + newWord);
            }
        }
        if(newPhrase.length() > 0) {
            // remove the leading space
            newPhrase.deleteCharAt(0);
        }
        return newPhrase.toString();
    }

    public static String trimPunctuation(String word) {
        while(word.length() > 0 && !Character.isLetter(word.charAt(0))) {
            word = word.substring(1);
        }
        while(word.length() > 0 && !Character.isLetter(word.charAt(word.length() - 1))) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }

    public static boolean isCapitalized(String word) {
        if(word.equals("I") || word.equals("I'm") || word.equals("I'll") || word.equals("I'd")) {
            // these don't count
            return false;
        }
        return word != null && word.length() > 0 && Character.isUpperCase(word.charAt(0));
    }

    public List<List<String>> getLyricsNew(String seedLine, Objectify ofy) {
        String[] words = seedLine.split("\\s+");
        String lastWord = words[words.length - 1];

        Util.log("Let's see what rhymes with " + lastWord);
        List<String> rhymes = dictionary.getRhymes(lastWord, 10);
        Util.log(rhymes != null ? rhymes.toString() : "Um, I need to look this word up.");

        String significantWord = getSignificantWord(seedLine);
        Util.log("This song should be mostly about " + significantWord);

        List<String> fullLines = new ArrayList<String>();
        for(String rhyme: rhymes) {
            List<String> fragments = TwitterUtil.getTwitterLines(significantWord, rhyme, true);
            fullLines.addAll(fragments);

            //Util.log("Here are some kickass lines:");
            //Util.log(trimmedLines.toString());
            //for(String line : trimmedLines) {
            //    Util.log(line);
            //}

            //List<List<String>> groupedLines = groupLinesByRhyme(uniqueFragments, rhymes);

        }

        return assembleLines(seedLine, fullLines, rhymes);

    }


    public static int IDEAL_LINE_LENGTH = 7;
    public static int SYLLABLE_DIFFERENCE_THRESHOLD = 2;
    public static List<List<String>> assembleLines(String seedLine, List<String> fullLines, List<String> rhymes) {
        List<List<String>> verses = getVersesFromLines(seedLine, TwitterUtil.chopLines(fullLines, null), rhymes);
        if(verses.size() == 0) {
            System.out.println("tough crowd. Let's try using rhymechopping");
            verses = getVersesFromLines(seedLine, TwitterUtil.chopLines(fullLines, rhymes), rhymes);
        }
        return verses;
    }

    public static List<List<String>> getVersesFromLines(String seedLine, List<String> lines, List<String> rhymes) {
        boolean seedLineIsDouble = false;
        int lineLength = SyllableUtil.getSyllableCountFromLine(seedLine);
        if(lineLength > 18) {
            Util.log("What, are you Bob Dylan? This line is way too long to make a song out of");
        } else if(lineLength > 10) {
            // we'll want to split the seed line across two lines of lyrics
            lineLength /= 2;
            seedLineIsDouble = true;
        }
        // we have to help some people out!
        int targetLength = (lineLength + IDEAL_LINE_LENGTH) / 2;

        List<List<String>> verses = new ArrayList<List<String>>();
        List<String> doubleLineRhymes = new ArrayList<String>();
        List<String> singleLineRhymes = new ArrayList<String>();
        List<String> fillerLines = new ArrayList<String>();

        List<List<String>> groupedLines = groupLinesByRhyme(lines, rhymes);

        for(int i = 0; i < groupedLines.size(); i++) {
            List<String> group = groupedLines.get(i);
            for(String line : group) {
                int syllableCount = SyllableUtil.getSyllableCountFromLine(line);
                boolean isRhymingLine = i + 1 < groupedLines.size();
                if(isRhymingLine && isDoubleLine(syllableCount, targetLength, SYLLABLE_DIFFERENCE_THRESHOLD)) {
                    doubleLineRhymes.add(line);
                    //Util.log("Rhyming double line! " + line);
                } else if(isRhymingLine && isSingleLine(syllableCount, targetLength, SYLLABLE_DIFFERENCE_THRESHOLD)) {
                    singleLineRhymes.add(line);
                    //Util.log("Rhyming single line! " + line);
                } else if(!isRhymingLine && isSingleLine(syllableCount, targetLength, SYLLABLE_DIFFERENCE_THRESHOLD)) {
                    fillerLines.add(line);
                    //Util.log("Nonrhyming single line! " + line);
                }
            }
        }

        while(true) {
            List<String> verse = new ArrayList<String>();
            if(doubleLineRhymes.size() > 0) {
                verse.addAll(splitLine(doubleLineRhymes.remove(0)));
            } else if(singleLineRhymes.size() > 0 && fillerLines.size() > 0) {
                verse.add(fillerLines.remove(0));
                verse.add(singleLineRhymes.remove(0));
            } else {
                break;
            }

            if(seedLineIsDouble) {
                verse.addAll(splitLine(seedLine));
            } else if(fillerLines.size() > 0) {
                verse.add(fillerLines.remove(0));
                verse.add(seedLine);
            } else {
                break;
            }
            verses.add(trimPhrasePunctuation(verse));
        }
        return verses;
    }

    public static boolean isSingleLine(int syllableCount, int targetLength, int epsilon) {
        return syllableCount >= targetLength - epsilon &&
                syllableCount <= targetLength + epsilon;
    }

    public static boolean isDoubleLine(int syllableCount, int targetLength, int epsilon) {
        return syllableCount >= 2 * (targetLength - epsilon) &&
                syllableCount <= 2 * (targetLength + epsilon);
    }

    public static List<String> splitLine(String line) {
        int targetSize = SyllableUtil.getSyllableCountFromLine(line) / 2;
        String firstLine = "";
        String secondLine = "";
        int syllableTally = 0;
        String[] words = line.split("\\s+");
        for(String word : words) {
            syllableTally += SyllableUtil.getSyllableCountFromWord(word);
            if(syllableTally <= targetSize) {
                firstLine = firstLine + " " + word;
            } else {
                secondLine = secondLine + " " + word;
            }
        }
        return Arrays.asList(new String[] {firstLine.trim(), secondLine.trim()});
    }


    public static boolean isRhymingLine(String line, List<String> rhymes) {
        String[] words = line.split("\\s+");
        String lastWord = words[words.length - 1].toLowerCase();
        return rhymes.indexOf(lastWord) >= 0;
    }

    public static List<List<String>> groupLinesByRhyme(List<String> lines, List<String> rhymes) {
        List<List<String>> groupedLines = new ArrayList<List<String>>();
        int rhymeCount = rhymes.size();
        for(int i = 0; i <= rhymeCount; i++) {
            groupedLines.add(new ArrayList<String>());
        }

        for(String line : lines) {
            String[] words = line.split("\\s+");
            String lastWord = words[words.length - 1].toLowerCase();
            int rhymeIndex = rhymes.indexOf(lastWord);
            if(rhymeIndex < 0) {
                // unrhymed lines will be grouped at the end
                groupedLines.get(rhymeCount).add(line);
            } else {
                groupedLines.get(rhymeIndex).add(line);
            }
        }

        // remove all empty groups, except the unrhymed
        for(int i = rhymeCount - 1; i >= 0; i--) {
            if(groupedLines.get(i).size() == 0) {
                groupedLines.remove(i);
            }
        }
        return groupedLines;
    }
}

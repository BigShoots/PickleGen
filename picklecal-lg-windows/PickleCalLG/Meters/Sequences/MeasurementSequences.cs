using System;
using System.Collections.Generic;

namespace PickleCalLG.Meters.Sequences
{
    public static class MeasurementSequences
    {
        public static MeasurementSequence Grayscale10Point(bool useAveraging = true)
        {
            var steps = new List<MeasurementStep>();
            var ireValues = new[] { 0, 5, 10, 20, 30, 40, 50, 60, 80, 100 };
            foreach (var value in ireValues)
            {
                steps.Add(new MeasurementStep(
                    name: $"Gray {value}",
                    targetIre: value,
                    mode: MeterMeasurementMode.Display,
                    integrationTime: TimeSpan.Zero,
                    useAveraging: useAveraging,
                    patternDescription: $"Gray {value}%"));
            }

            return new MeasurementSequence("10-Point Grayscale", steps);
        }

        public static MeasurementSequence PrimarySecondarySweep(bool useAveraging = false)
        {
            var colors = new (string Name, string Pattern)[]
            {
                ("White", "White 100%"),
                ("Red", "Red 100%"),
                ("Green", "Green 100%"),
                ("Blue", "Blue 100%"),
                ("Cyan", "Cyan 100%"),
                ("Magenta", "Magenta 100%"),
                ("Yellow", "Yellow 100%")
            };

            var steps = new List<MeasurementStep>();
            foreach (var color in colors)
            {
                steps.Add(new MeasurementStep(
                    name: color.Name,
                    targetIre: 100,
                    mode: MeterMeasurementMode.Display,
                    integrationTime: TimeSpan.Zero,
                    useAveraging: useAveraging,
                    patternDescription: color.Pattern));
            }

            return new MeasurementSequence("Primary & Secondary Sweep", steps);
        }
    }
}
